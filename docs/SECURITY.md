# Seguridad — Clínica

**Versión:** 1.0  
**Stack:** Spring Security 6 · JWT RSA-256 · Redis · Resilience4j

---

## 1. Modelo de Seguridad

El sistema implementa múltiples capas de seguridad que operan de forma independiente. El fallo de una capa no compromete las demás.

```
Cliente
  │
  ▼
[1] TLS / HTTPS ────────── cifrado en tránsito
  │
  ▼
[2] Rate Limiting ─────── protección contra fuerza bruta / DoS
  │
  ▼
[3] JWT Validation ─────── identidad y roles verificados
  │
  ▼
[4] RBAC ──────────────── autorización por rol
  │
  ▼
Microservicio
  │
  ▼
[5] Audit Log ──────────── trazabilidad completa
```

---

## 2. Autenticación — JWT RSA-256

### 2.1 Arquitectura Asimétrica

```
Auth Service                    Microservicios
     │                               │
     │  Tiene:                       │  Tienen:
     │  ┌─────────────────┐          │  ┌─────────────────┐
     │  │ Clave privada   │          │  │ Clave pública   │
     │  │ (firma tokens)  │          │  │ (verifica firma)│
     │  └─────────────────┘          │  └─────────────────┘
     │                               │
     │  JWT firmado                  │
     │ ──────────────────────────►   │
     │                               │  ¿Firma válida? → permitir
```

**Ventaja de seguridad:** si un microservicio es comprometido, el atacante solo obtiene la clave pública — no puede emitir tokens nuevos. Solo el Auth Service puede firmar.

### 2.2 Estructura del Token

```
Header:
{
  "alg": "RS256",
  "typ": "JWT"
}

Payload:
{
  "sub": "a1b2c3d4-...",          // userId (UUID)
  "username": "dr.martinez",
  "roles": ["ROLE_DOCTOR"],
  "iat": 1716134400,              // issued at
  "exp": 1716135300               // expires at (15 min)
}

Signature:
  RSA-SHA256(base64(header) + "." + base64(payload), privateKey)
```

### 2.3 Tiempos de Vida

| Token         | Duración | Renovable |
| ------------- | -------- | --------- |
| Access token  | 15 min   | Sí, con refresh token |
| Refresh token | 7 días   | No — requiere login nuevo al expirar |

Los access tokens de corta vida limitan la ventana de exposición si un token es interceptado.

### 2.4 Flujo de Autenticación

```
[1] Cliente → POST /api/v1/auth/login
      {username, password}

[2] Auth Service:
      · Busca usuario por username
      · Verifica BCrypt(password, hash_almacenado)
      · Si OK: genera access token (RSA-256) + refresh token UUID
      · Guarda refresh token en base de datos (hasheado)
      · Registra evento en AuditLog

[3] Respuesta:
      {accessToken, refreshToken, expiresIn: 900}

[4] Cliente guarda tokens (seguro, no en localStorage)

[5] Requests subsecuentes:
      Authorization: Bearer <accessToken>

[6] Al expirar (401):
      POST /api/v1/auth/refresh
      {refreshToken} → nuevo accessToken
```

### 2.5 Logout y Revocación

```
POST /api/v1/auth/logout
  Authorization: Bearer <accessToken>

Auth Service:
  · Marca el refresh token como REVOKED en base de datos
  · Añade el access token a una blacklist en Redis:
        SHA-256(token) → "revoked", con TTL = vida restante del token
  · Registra evento de logout en AuditLog

API Gateway, en cada request:
  · Valida la firma RSA del token
  · Consulta la blacklist antes de enrutar → si está, 401
```

Existe además `POST /api/v1/auth/logout-all` para cerrar todas las sesiones del usuario:
revoca en lote todos sus refresh tokens y añade el access token actual a la blacklist.

**Dos decisiones de diseño en la blacklist:**

- **Se almacena el hash SHA-256 del token, nunca el token.** Un volcado de Redis no entrega
  credenciales utilizables.
- **El TTL es la vida restante del propio token.** Pasado ese punto el token expira por sí
  mismo y la entrada sobra, así que Redis la elimina solo: la blacklist no crece sin límite
  y no necesita proceso de limpieza.

**Degradación si Redis no está disponible:** la comprobación de blacklist falla *en abierto*
(ver §5.5). La firma del token se sigue validando siempre, así que no se aceptan tokens
falsos; lo que se pierde es la revocación anticipada durante la caída, con una exposición
acotada a la vida del access token (15 min).

---

## 3. Bloqueo de Cuenta

El sistema registra intentos de login fallidos y bloquea automáticamente la cuenta para prevenir ataques de fuerza bruta.

```
Intento de login fallido:
  1. Incrementa failed_attempts en tabla users
  2. Si failed_attempts >= MAX_ATTEMPTS (configurable):
     · Establece account_locked_until = NOW() + LOCKOUT_DURATION
     · Registra evento ACCOUNT_LOCKED en AuditLog

Login con cuenta bloqueada:
  → HTTP 423 Locked
  → Mensaje: "Cuenta bloqueada. Intente nuevamente después de las HH:MM."

Desbloqueo:
  · Automático: cuando account_locked_until < NOW()
  · Manual: administrador puede resetear failed_attempts
```

---

## 4. RBAC — Control de Acceso Basado en Roles

### 4.1 Roles del Sistema

| Rol                  | Descripción                                       |
| -------------------- | ------------------------------------------------- |
| `ROLE_ADMIN`         | Acceso total — gestión de usuarios y configuración |
| `ROLE_DOCTOR`        | Gestión de atenciones, acceso a historias clínicas |
| `ROLE_NURSE`         | Triage, actualización de estados de atención      |
| `ROLE_RECEPTIONIST`  | Registro de pacientes, creación de atenciones     |
| `ROLE_BILLING`       | Acceso al módulo de facturación                   |
| `ROLE_MEDICAL_RECORDS` | Archivo clínico — copias de la historia para el paciente, sin editarla |

### 4.2 Matriz de Permisos

| Recurso                      | ADMIN | DOCTOR | NURSE | RECEPTIONIST | BILLING | MEDICAL_RECORDS |
| ---------------------------- | ----- | ------ | ----- | ------------ | ------- | --------------- |
| Crear paciente               | ✓     | ✓      | ✗     | ✓            | ✗       | ✗               |
| Ver historia clínica         | ✗     | ✓ ¹    | ✓ ¹   | ✗            | ✗       | ✗               |
| Escribir en la historia      | ✗     | ✓ ¹    | ✓ ¹   | ✗            | ✗       | ✗               |
| Copia de la historia al paciente | ✗ | ✗      | ✗     | ✗            | ✗       | ✓               |
| Verificar integridad y firma | ✓     | ✗      | ✗     | ✗            | ✗       | ✓               |
| Crear atención               | ✓     | ✓      | ✓     | ✓            | ✗       | ✗               |
| Cambiar estado atención      | ✓     | ✓      | ✓     | ✗            | ✗       | ✗               |
| Acceder facturación          | ✓     | ✗      | ✗     | ✗            | ✓       | ✗               |
| Gestionar médicos            | ✓     | ✗      | ✗     | ✗            | ✗       | ✗               |
| Gestionar usuarios           | ✓     | ✗      | ✗     | ✗            | ✗       | ✗               |
| Chat con IA                  | ✓     | ✓      | ✓     | ✓            | ✗       | ✗               |

¹ El rol solo habilita: para ver o escribir hace falta además relación de cuidado con esa atención
(sección 7.6). Los roles administrativos no ven contenido clínico.

### 4.3 Implementación

El rol viaja dentro del JWT en el claim `role`. La librería `clinica-commons-security` valida el token
como OAuth2 Resource Server (firma RS256, issuer `ClinicaDeYmid`, expiración, `sub` presente y
`type = access`), convierte el rol en la autoridad `ROLE_<ROL>` y cada controlador aplica
`@PreAuthorize`:

```java
@GetMapping("/{uuid}")
@PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'DOCTOR', 'NURSE', 'RECEPTIONIST')")
ResponseEntity<PatientDetailsView> get(@PathVariable UUID uuid) { ... }

@PostMapping("/{uuid}/deactivation")
@PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
ResponseEntity<PatientView> deactivate(@PathVariable UUID uuid, ...) { ... }
```

No se realiza ninguna llamada al Auth Service en tiempo de request — los roles están en el token, la validación es local.

---

## 5. Rate Limiting

El API Gateway aplica dos límites independientes —por usuario y por IP— con contadores de
ventana fija en Redis.

### 5.1 Algoritmo — Contador de Ventana Fija

Implementado con operaciones atómicas de Redis (`INCR` + `EXPIRE`), no con token bucket.

```
Por cada request:
  count = INCR(clave)
  IF count == 1:
    EXPIRE(clave, 60s)          # arranca la ventana en el primer hit
  IF count <= LIMITE:
    ALLOW
  ELSE:
    RETURN 429 Too Many Requests

Al expirar la clave, el contador desaparece y la ventana se reinicia.
```

`INCR` es atómico en Redis, así que el conteo es correcto aunque haya varias instancias del
gateway compartiendo la misma instancia de Redis.

**Limitación conocida:** en una ventana fija, 100 peticiones en el segundo 59 y otras 100 en
el 61 suman 200 en dos segundos reales. Un token bucket con recarga continua suaviza ese
efecto de borde, a costa de un script Lua para mantener la atomicidad de leer-recargar-decrementar.

### 5.2 Límites Aplicados

| Clave Redis            | Límite        | Motivo                                                        |
| ---------------------- | ------------- | ------------------------------------------------------------- |
| `rate_limit:user:<id>` | 100 req/min   | Impide extracción masiva desde una cuenta comprometida        |
| `rate_limit:ip:<ip>`   | 1000 req/min  | Cubre endpoints sin usuario autenticado (login); umbral alto porque tras un NAT hay muchos usuarios legítimos |

La IP se resuelve leyendo `X-Forwarded-For`, luego `X-Real-IP`, y por último la dirección
remota de la conexión.

### 5.3 Cada Límite en su Punto de la Cadena

Los dos límites no son intercambiables: protegen de ataques distintos y por eso se evalúan en
momentos distintos del pipeline del gateway.

```
Request
   │
   ▼
IpRateLimitFilter        orden 0    ← antes de autenticar
   │                                  cubre el login y cualquier endpoint público,
   │                                  que es donde ataca quien no tiene credenciales
   ▼
AuthenticationFilter     orden 1    ← filtro de ruta; valida la firma RSA,
   │                                  consulta la blacklist y publica la identidad
   │                                  en el atributo AUTHENTICATED_USER_ID
   ▼
UserRateLimitFilter      orden 10   ← después de autenticar
   │                                  la identidad ya es de fiar; frena la extracción
   │                                  masiva desde una cuenta comprometida
   ▼
Servicio destino
```

Spring Cloud Gateway combina filtros globales y de ruta en una sola cadena ordenada, y asigna
a cada filtro de ruta el orden `índice + 1`. Como `AuthenticationFilter` es `filters[0]` en
todas las rutas, su orden efectivo es **1**; de ahí los valores 0 y 10 elegidos para los dos
filtros de rate limiting.

**La identidad se lee de un atributo del intercambio, no de la cabecera `X-User-ID`.** Un
cliente puede fabricar cabeceras: si el contador se llevara por cabecera, bastaría con rotar
un identificador falso en cada petición para tener siempre un contador nuevo y anular el
límite. Un atributo del `ServerWebExchange` solo lo escribe un filtro de este proceso, después
de haber verificado la firma del token. La cabecera se sigue enviando al servicio destino, y
`AuthenticationFilter` la reescribe siempre, de modo que un `X-User-ID` entrante nunca
atraviesa el gateway.

En rutas públicas no hay identidad y `UserRateLimitFilter` no interviene; ahí la única
protección es el límite por IP, que es exactamente el reparto buscado.

> **Nota histórica.** Ambos límites vivían en un único filtro global de orden 0 que leía
> `X-User-ID` antes de que `AuthenticationFilter` la inyectara, así que la rama por usuario
> nunca llegaba a activarse y en la práctica solo operaba el límite por IP. Separarlos en dos
> filtros con órdenes explícitos corrige el fallo y hace visible en el código por qué cada
> límite va donde va.

### 5.4 Cabeceras de Respuesta

Cuando se supera el límite, la respuesta `429` incluye:

```
X-RateLimit-Remaining: 0
Retry-After: 60
```

Las respuestas permitidas no llevan cabeceras de rate limiting.

### 5.5 Comportamiento ante Fallo de Redis

Si Redis no responde, el rate limiting **falla en abierto**: se permite el request y se
registra el error. Es una decisión consciente — una caída de la cache no debe tumbar un
sistema hospitalario, y el rate limiting es una capa de protección, no de autenticación:
la validación de la firma del JWT sigue operando con normalidad.

En un entorno con requisitos regulatorios de revocación inmediata, la blacklist de tokens
(§2.5) debería pasar a fallar en cerrado, manteniendo el fail-open solo aquí.

### 5.6 Escenarios de Protección

| Escenario              | Protección                                    |
| ---------------------- | --------------------------------------------- |
| Fuerza bruta en login  | Rate limit por IP + bloqueo de cuenta tras N fallos |
| Scraping de pacientes  | Rate limit por userId impide extracción masiva |
| DoS desde un usuario   | 429 rápido, sin carga al microservicio        |
| DoS desde IP anónima   | Rate limit por IP aplicado antes de validar JWT |

---

## 6. Auditoría

Cada evento de seguridad queda registrado en la tabla `audit_logs` del Auth Service.

### 6.1 Eventos Registrados

| Evento                | Campos guardados                                     |
| --------------------- | ---------------------------------------------------- |
| `LOGIN_SUCCESS`       | userId, username, IP, timestamp                      |
| `LOGIN_FAILED`        | username, IP, timestamp, intento número N            |
| `ACCOUNT_LOCKED`      | userId, IP, timestamp                                |
| `LOGOUT`              | userId, IP, timestamp                                |
| `TOKEN_REFRESHED`     | userId, timestamp                                    |
| `PASSWORD_CHANGED`    | userId, IP, timestamp                                |
| `PASSWORD_RESET`      | userId, email, IP, timestamp                         |

### 6.2 Soft Delete y Auditoría de Datos

Todas las entidades clínicas tienen campos de trazabilidad:

```sql
deleted_at   DATETIME    NULL   -- null = activo
deleted_by   VARCHAR(50) NULL   -- username que eliminó
created_at   DATETIME    NOT NULL DEFAULT NOW()
updated_at   DATETIME    NOT NULL DEFAULT NOW() ON UPDATE NOW()
created_by   VARCHAR(50) NOT NULL
updated_by   VARCHAR(50) NOT NULL
```

Esto garantiza:
- ¿Quién creó un registro? → `created_by`
- ¿Quién lo modificó por última vez? → `updated_by`
- ¿Quién lo "eliminó" y cuándo? → `deleted_by`, `deleted_at`
- Reproducibilidad completa del estado de cualquier registro en cualquier momento

---

## 7. Seguridad en Tránsito

```
Externo → Gateway:      HTTPS (TLS 1.2/1.3) — obligatorio en producción
Gateway → Microservicio: HTTP interno en red Docker (red privada, sin exposición pública)
Microservicio → BD:      Conexión autenticada por usuario/contraseña de base de datos
```

Las credenciales de `patient-service`, `clinical-history-service` y su infraestructura viven en OpenBao
(sección 7.7); los servicios que aún no se refactorizan siguen recibiéndolas por variables de entorno.

### 7.1 Mínimo privilegio en la base de pacientes

`patient-db` crea dos usuarios con `patient-service/docker/mysql-init/01-create-users.sh`:

| Usuario            | Uso                         | Permisos                                              |
| ------------------ | --------------------------- | ----------------------------------------------------- |
| `patient_migrator` | Flyway, solo al arrancar    | DDL y DML sobre `patient_db`                          |
| `patient_app`      | Conexiones de la aplicación | `SELECT`, `INSERT`, `UPDATE` sobre pacientes (sin `DELETE`, DDL ni `GRANT`); `SELECT`, `INSERT`, `DELETE` solo en `patient_outbox` |
| `patient_debezium` | Debezium (Kafka Connect)    | `SELECT` solo en `patient_outbox`; `REPLICATION SLAVE` y `REPLICATION CLIENT` |

La base solo está en la red interna `patient-data`, compartida únicamente con `patient-service`, y no
publica puertos (el archivo `docker-compose.debug.yml` los abre en `127.0.0.1` para depurar).
`DatabaseAccessIT` verifica con MySQL real que `patient_app` no puede borrar, alterar el esquema,
crear triggers ni concederse permisos.

**Límite conocido de Debezium:** en MySQL el permiso de replicación es global, así que técnicamente
permite leer el binlog de todo el servidor. Se mitiga con credenciales exclusivas, `kafka-connect` sin
puertos publicados y solo en las redes que necesita, y porque el usuario no puede consultar tablas
fuera del outbox. Para aislarlo por completo haría falta un servidor MySQL dedicado al outbox, lo que
rompería la atomicidad de la transacción.

### 7.2 Eventos y datos personales

- Los eventos solo llevan los datos que necesitan los consumidores: documento, nombres, fecha de
  nacimiento, sexo, estado y afiliación. Nunca contacto, residencia ni motivos de desactivación
  (`PatientEventContractTest` lo verifica).
- El outbox está en un esquema aparte (`patient_outbox`) para que Debezium no tenga acceso a las tablas
  del registro, y sus filas se purgan a los 7 días.
- Kafka corre en la red interna `kafka-net` sin puertos publicados. En producción debe añadirse TLS y
  ACL por consumidor.

### 7.3 Datos personales en respuestas y logs

- Las búsquedas por documento o nombre usan `POST` para que esos datos no queden en URLs.
- Los errores nunca repiten valores recibidos ni detalles de SQL; incluyen `code` y `traceId`.
- `IdentityDocument` se imprime enmascarado (`CEDULA_DE_CIUDADANIA:******5432`) y los demás valores
  personales se imprimen como `[redacted]`.

### 7.4 Mínimo privilegio en la base de la historia clínica

`clinical-db` reparte el dominio en cuatro esquemas y crea tres usuarios con
`clinical-history-service/docker/mysql-init/01-create-users.sh`:

| Esquema              | Contenido                                   | `clinical_app`                      |
| -------------------- | ------------------------------------------- | ----------------------------------- |
| `clinical_db`        | Atenciones, copia local de pacientes, catálogos | `SELECT`, `INSERT`, `UPDATE`        |
| `clinical_ledger`    | Notas firmadas y cadena de hashes           | `SELECT`, `INSERT`, `LOCK TABLES`   |
| `clinical_workspace` | Borradores                                  | `SELECT`, `INSERT`, `UPDATE`, `DELETE` |
| `clinical_keys`      | DEK por paciente envueltas                  | `SELECT`, `INSERT`                  |
| `clinical_outbox`    | Eventos por publicar                        | `SELECT`, `INSERT`, `DELETE`        |

La aplicación no puede borrar ni modificar nada de `clinical_ledger` ni de `clinical_keys`: rotar una
clave es escribir una envoltura nueva, no actualizar la anterior. `clinical_migrator` tampoco recibe
`UPDATE` ni `DELETE` sobre el libro, así que una migración mal escrita no puede reescribir la
historia. `clinical_debezium` solo lee el outbox.

La base vive en la red interna `clinical-data` con su almacenamiento de anexos, sin puertos
publicados, y está cifrada en reposo: imagen propia con `component_keyring_file`,
`default_table_encryption` activo y redo, undo y binlog cifrados. Si el archivo del keyring no existe
con el dueño correcto, la base no arranca.

### 7.5 Firma, cifrado y retención de la historia clínica

- **Inmutable por construcción.** Una nota firmada no se corrige: se aclara o se anula con motivo, y
  ambas quedan encadenadas. Cada firma calcula el SHA-256 de un JSON canónico, lo sella con una clave
  ECDSA P-256 guardada fuera de la base y lo encadena por paciente. `GET /patients/{uuid}/integrity`
  recalcula la cadena; `clinical.encounters.v1` publica la cabeza para que un tercero pueda detectar
  incluso el truncamiento del final de la cadena.
- **Cifrado extremo del contenido.** Todo el texto clínico se cifra con AES-GCM y una DEK por
  paciente, envuelta por una clave maestra en disco (`CLINICAL_ENCRYPTION_KEYS_LOCATION`) que nunca
  entra en la base. El AAD ata cada bloque a su propósito y a su registro. Perder la clave maestra
  vuelve ilegible la historia: el runbook de rotación y recuperación está en
  `clinical-history-service/docs/claves-y-cifrado.md`.
- **Firmar exige autenticación reciente.** El token debe haberse emitido hace menos de 15 minutos; si
  no, la firma responde `403 RECENT_AUTHENTICATION_REQUIRED`.
- **Anexos con retención WORM.** El archivo se cifra antes de subirlo y, al firmar, se copia a un
  bucket con Object Lock en modo `COMPLIANCE` hasta la firma + 15 años + 1 día; una tarea diaria
  extiende la retención con cada atención nueva. El servicio se niega a archivar si el bucket no
  tiene Object Lock activo. No hay URLs firmadas: toda descarga pasa por el servicio, que verifica el
  acceso, audita y comprueba el SHA-256.
- **Auditoría de lectura.** Cada decisión de acceso —consultas, escrituras y rechazos— se escribe en
  el outbox dentro de su propia transacción y se publica en `clinical.access-audit.v1` con retención
  indefinida. Los rechazos se auditan igual que los accesos concedidos.

### 7.6 Acceso a la historia clínica

El rol no basta: hace falta una **relación de cuidado**, que es pertenecer al equipo de la atención.
Quien abre la atención entra en el equipo; sumar a alguien más solo se puede con la atención abierta.
La relación vale mientras la atención esté abierta y 30 días después de cerrada. Escribir, cerrar o
anular exige ser del equipo de esa atención concreta.

| Situación                                        | Quién entra                                              |
| ------------------------------------------------ | -------------------------------------------------------- |
| Contenido clínico de una atención                | Equipo de cuidado de esa atención                        |
| Notas restringidas (salud mental, salud sexual, VIH, violencia) | Solo el autor y el equipo de su atención   |
| Urgencia sin relación previa (romper el vidrio)  | Motivo de 10 caracteres o más, dura 4 h, cifrado y auditado |
| Verificación de integridad y firma               | `ADMIN`, `SUPER_ADMIN` y `MEDICAL_RECORDS`, sin contenido clínico |
| Copia de la historia para el paciente            | `MEDICAL_RECORDS`, con motivo y auditoría                |
| Administración del catálogo CIE-10 y rotación de claves | `SUPER_ADMIN`                                     |

Los roles administrativos no ven contenido clínico y recepción no accede a la historia. Abrir una
atención no exige relación previa —así empieza el cuidado— pero queda auditado.

---

### 7.7 Gestión de secretos

- **Un gestor aparte, no auth-service.** Los secretos viven en un clúster OpenBao de tres nodos; auth
  solo se ocupa de identidades. Si OpenBao cae, los servicios que ya arrancaron siguen funcionando.
- **Mínimo privilegio por consumidor.** Cada servicio se autentica con su propio AppRole y su política
  solo permite leer sus rutas: `patient-service` no puede leer secretos de la historia clínica ni la
  contraseña root de su propia base. El agente de infraestructura solo lee.
- **Nada sensible en la configuración de los contenedores.** Los servicios Spring reciben los secretos
  en memoria; MySQL, Kafka Connect y el almacenamiento los leen de archivos (`*_FILE`,
  `DirectoryConfigProvider`) en volúmenes montados solo en ese contenedor. `openbao-e2e.sh` comprueba
  que ninguna contraseña aparezca en `docker inspect`.
- **TLS 1.3 y auditoría.** Todo el tráfico con OpenBao va cifrado y cada petición queda en el registro
  de auditoría de los nodos.
- **Desarrollo frente a producción.** En desarrollo el sello es una clave estática en un volumen y el
  token root queda en `openbao_bootstrap`; en producción se usa auto-unseal con KMS/HSM, se revoca el
  token root, los nodos van en máquinas distintas y el `secret-id` se entrega con la identidad de la
  plataforma.

## 8. Decisiones de Diseño

### 8.1 RSA-256 en lugar de HMAC-SHA256

**Problema:** con HMAC, todos los microservicios necesitan la misma clave secreta para verificar tokens. Un secreto compartido entre N servicios multiplica la superficie de ataque por N.

**Solución:** clave privada RSA solo en el Auth Service (firma). Clave pública en cada microservicio (verificación). Comprometer un microservicio no expone la capacidad de emitir tokens.

### 8.2 Refresh Token Hasheado en Base de Datos

**Problema:** si la base de datos de Auth es comprometida, los refresh tokens expuestos permitirían impersonar usuarios indefinidamente.

**Solución:** el refresh token almacenado en base de datos es el hash SHA-256 del valor real. El cliente guarda el valor original; la base de datos guarda el hash. Un dump de la base de datos no revela tokens usables.

### 8.3 No Verificación de Roles en Auth Service por Request

**Problema:** centralizar la autorización en Auth Service crea un punto único de fallo. Si cae, ningún microservicio puede verificar permisos.

**Solución:** los roles viajan en el JWT. Cada microservicio valida la firma del token (con clave pública, sin llamada de red) y aplica RBAC localmente. Auth Service no está en el hot path de cada request.

### 8.4 Rate Limiting en Gateway, No en Microservicios

**Problema:** si el rate limiting está en cada microservicio, un atacante puede bypassearlo llamando directamente a puertos internos.

**Solución:** el rate limiting se aplica en el API Gateway, que es el único punto de entrada expuesto. Los puertos de los microservicios no son accesibles desde fuera de la red Docker.

---

_Documento mantenido junto al código — las configuraciones específicas (umbrales, duraciones) se definen en `application.yml` de cada servicio._
