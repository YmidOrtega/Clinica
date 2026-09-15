# Seguridad — Clínica

**Versión:** 1.0  
**Stack:** Spring Authorization Server · JWT ES256 firmado en OpenBao transit · Gateway BFF con Redis

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

## 2. Autenticación — OAuth 2.1 / OIDC con tokens ES256

`auth-service` es un servidor de autorización (Spring Authorization Server 1.5). Contrato de la API de
login en `BackEnd-Clinica/auth-service/docs/flujo-de-login.md`.

### 2.1 Firma sin clave privada en el servicio

```
auth-service ──"firma este JWT"──► OpenBao transit (auth-jwt, ecdsa-p256, no exportable)
     │◄──────── firma ES256 ──────────┘
     │
     └── /oauth2/jwks: claves públicas de la versión activa y la anterior (kid = auth-jwt-vN)
```

- La clave privada nunca sale de OpenBao: ni auth-service ni los demás servicios la tienen. Rota sola cada
  30 días (`auto_rotate_period`) y el JWKS publica también la versión anterior mientras sus tokens viven.
- La clave usada antes de este cambio (RS256) se había publicado en el historial del repositorio; no se
  importó y ningún token firmado con ella es aceptado por `auth-service`.
- Si OpenBao no responde, `auth-service` no emite tokens ni verifica códigos TOTP: responde
  `503 {"error": "temporarily_unavailable"}` con `Retry-After: 5`, y los tokens ya emitidos siguen
  validando en los servicios con el JWKS en caché.

### 2.2 Authorization code con PKCE y cliente confidencial

- El navegador no recibe tokens. El gateway es el cliente OAuth (patrón BFF) y se autentica en
  `/oauth2/token` con `private_key_jwt`: firma una aserción con su propia clave de transit
  (`api-gateway-client`) y `auth-service` la verifica con la clave pública que lee de OpenBao.
- PKCE es obligatorio y no hay pantalla de consentimiento (clientes propios).
- El gateway guarda los tokens en su Redis, nunca en el navegador, y los agrega a cada llamada a la API.
  Serializa la renovación por sesión con un candado en Redis: dos peticiones en paralelo nunca usan el
  mismo refresh token, que `auth-service` trataría como robo. Contrato para el frontend en
  `BackEnd-Clinica/api-gateway/docs/bff.md`.
- El frontend está en otro origen: el gateway permite CORS con credenciales solo para
  `GATEWAY_FRONTEND_ORIGINS`, entrega el token CSRF en `GET /bff/session` y quita hacia los servicios
  las cookies, las cabeceras CSRF y cualquier `Authorization` o `X-Forwarded-*` que mande el navegador.
- El login lo hacen las pantallas del frontend contra una API JSON con sesión en Spring Session JDBC y
  CSRF de sesión. Al autenticarse se cambia el identificador de sesión.

### 2.3 Segundo factor obligatorio y step-up

- Todo el personal usa **TOTP** (RFC 6238, 6 dígitos, 30 s). El secreto se genera y se guarda en el motor
  TOTP de OpenBao (clave `totp/keys/staff-<uuid>`); `auth-service` solo pide validar códigos y la política
  no le deja leer ni listar las claves. OpenBao rechaza un código ya usado aunque siga en su ventana.
- El primer login después de activar la cuenta obliga a enrolarse; al confirmar se entregan una sola vez
  **10 códigos de recuperación** de un solo uso, guardados como SHA-256.
- El segundo factor tiene su propio frenado: 5 fallos seguidos → `429` con espera creciente.
- Un administrador puede reiniciar el segundo factor de otra persona con un motivo (el propio no): se
  borra su clave TOTP de OpenBao, se revocan sus códigos de recuperación y sus sesiones, y el siguiente
  login vuelve al enrolamiento. Cada persona puede regenerar sus propios códigos de recuperación.
- **Step-up (RFC 9470):** si el cliente pide `max_age` y el último factor verificado es más antiguo, la
  autorización redirige a reautenticarse con el segundo factor antes de emitir el código. El token lleva
  `auth_time` de esa verificación, `amr` (`pwd`, `otp` o `rec`, `mfa`) y `acr: urn:clinica:acr:mfa`, para
  que los servicios exijan una autenticación reciente en operaciones sensibles.
- En `auth-service` exigen un segundo factor de hace 5 minutos o menos: invitar usuarios, cambiar el rol,
  suspender, desactivar o reactivar, reiniciar el segundo factor de otra persona y regenerar los propios
  códigos de recuperación. Sin él responden `401` con
  `WWW-Authenticate: Bearer error="insufficient_user_authentication", max_age=300`.

### 2.4 Tokens y tiempos de vida

| Elemento | Duración | Notas |
|---|---|---|
| Access token (JWT ES256) | 5 min | `aud: clinica-api`, `role`, `email`, `name`, `auth_time`, `amr`, `acr` |
| Refresh token (opaco) | 12 h absolutas | Rota en cada uso |
| Sesión de `auth-service` | 15 min sin actividad, 12 h absolutas desde el login | Cookie `HttpOnly`, `SameSite=Lax`; el step-up no la alarga |
| Paso pendiente del login (cambio de contraseña, segundo factor) | 10 min | La sesión no queda autenticada hasta completar el segundo factor |
| Código de autorización | 1 min | Un solo uso |

### 2.5 Almacenamiento y revocación

- Códigos, access, refresh e id tokens se guardan **solo como SHA-256** en `auth_sessions`; un volcado
  de la base no entrega credenciales utilizables.
- **Familias de refresh:** cada refresh rotado queda registrado; si alguien reutiliza uno ya rotado
  (señal de robo), se revoca la autorización completa con todos sus tokens.
- **Máximo 5 sesiones vivas por usuario:** la sexta cierra la más antigua.
- **Sesiones visibles:** cada persona lista sus sesiones (`GET /api/v1/me/sessions`, marcando la actual)
  y cierra una concreta (`DELETE /api/v1/me/sessions/{id}`, auditado como `SessionClosed`); cerrar una
  borra su refresh token y la sesión no se puede renovar. Un `ADMIN` cierra todas las de un usuario que
  administra con step-up (`POST /api/v1/users/{uuid}/session-revocation`). Cambiar la propia contraseña
  también exige step-up.
- **API de usuarios:** `/api/v1/users` y `/api/v1/me` validan el access token (firma, emisor y
  `aud: clinica-api`) y además releen al usuario en cada petición: un token de alguien suspendido o con
  sesiones revocadas se rechaza sin esperar a que venza, y se aplica el rol actual.
- **`tokensNotBefore`:** suspender, desactivar, cambiar el rol, forzar cambio, reiniciar el segundo factor, cerrar todas las sesiones o resetear la contraseña lo
  mueve; desde ese instante `auth-service` rechaza refrescar las sesiones anteriores. El reseteo borra
  además todas las sesiones y autorizaciones del usuario.
- **Resolución de un segundo:** `iat` de un JWT va en segundos, así que un token vale solo si su `iat`
  es posterior al segundo de `tokensNotBefore` truncado. Un token emitido en el mismo segundo que la
  revocación se rechaza: quien vuelve a entrar justo después de un reseteo o de cerrar todas sus
  sesiones puede recibir un `401` y debe repetir el login un segundo después.

### 2.6 Validación en los servicios (`clinica-commons-security` 2.1.0)

- **Firma y destino:** cada servicio valida ES256 contra el JWKS de `auth-service` (en caché; si auth
  cae, las claves conocidas siguen sirviendo 24 h), el emisor, la vigencia y que `aud` incluya
  `clinica-api` o su propio nombre.
- **Revocación en segundos:** cada réplica lee `auth.users.v1` completo al arrancar y lo sigue en
  memoria. Rechaza con `401` el token de un usuario que no esté `ACTIVE` o cuyo `iat` no sea posterior
  al segundo de su `tokensNotBefore` (la misma regla de 2.5), sin esperar a que venza. Si Kafka no está disponible sigue validando con lo que
  ya conoce; un usuario del que aún no llegó ningún evento se acepta.
- **Personas y servicios separados:** un token de persona da `ROLE_<ROL>`; un token de servicio
  (`client_credentials`, `sub = client_id`) solo da `SCOPE_<scope>` y nunca pasa un `hasRole`, aunque
  traiga un claim `role`.
- **Llamadas en nombre de una persona:** el token del usuario no se reenvía. El servicio que llama lo
  intercambia en `auth-service` (RFC 8693, con su propio token como `actor_token`) por uno con `aud`
  exacta del destino, el mismo `sub`, rol, `auth_time` y `amr`, y el claim `act` con la cadena de
  servicios. `auth-service` solo permite las audiencias configuradas por cliente (hoy
  `clinical-history-service → patient-service`) y rechaza el intercambio si la persona fue suspendida.
  El token intercambiado vence a los 5 minutos o con el original, lo que ocurra antes; se guarda en
  caché por token original y destino.
- **Step-up:** `RecentAuthentication` exige segundo factor verificado hace 5 minutos o menos y responde
  `401` con `WWW-Authenticate: Bearer error="insufficient_user_authentication", max_age=300`. En clinical
  lo exigen firmar y anular notas, el acceso de emergencia, emitir la copia de la historia y el rewrap de
  claves.
- **Clientes de servicio:** `patient-service` y `clinical-history-service` se autentican con
  `private_key_jwt` firmando la aserción en OpenBao transit (`patient-service-client`,
  `clinical-history-service-client`); sus tokens propios duran 30 minutos y se renuevan antes de vencer.

---

## 3. Credenciales y frenado de intentos

**Contraseñas (NIST SP 800-63B-4).** Sin reglas de composición ni expiración periódica. La contraseña
se normaliza a Unicode NFKC y se exige:

- Entre 8 y 128 caracteres (el segundo factor es obligatorio, así que el mínimo es 8).
- No estar en la lista de contraseñas comunes (46 mil de SecLists/NCSC) ni ser una repetición o secuencia.
- No contener el correo, el nombre del usuario ni el nombre de la institución.

Se guarda con **Argon2id** (19 MiB, 2 iteraciones, 1 hilo, el mínimo de OWASP); el historial de Envers
nunca incluye el hash. Para que un correo inexistente cueste lo mismo que uno real, el login verifica
contra un hash señuelo.

**Frenado por capas en lugar de bloqueo.** Bloquear la cuenta tras pocos fallos permitiría a cualquiera
dejar a un médico sin acceso en plena urgencia. Los fallos se cuentan por separado, sin guardar el correo
ni la IP (solo su SHA-256):

| Clave            | Sin espera | Espera                               | Límite duro |
| ---------------- | ---------- | ------------------------------------ | ----------- |
| Cuenta + IP      | 5 fallos   | se duplica desde 1 s, máximo 15 min  | —           |
| Cuenta           | 10 fallos  | se duplica desde 1 s, máximo 1 min   | 100 fallos consecutivos |
| IP               | —          | limitador del gateway (sección 5)    | —           |

Tras 100 fallos consecutivos la cuenta queda bloqueada hasta un reseteo de contraseña o hasta que un
`ADMIN` la desbloquee. El bloqueo por intentos es independiente del estado administrativo del usuario.

---

## 4. RBAC — Control de Acceso Basado en Roles

### 4.1 Roles del Sistema

Los roles son fijos en el código de `auth-service` y cada usuario tiene uno solo; los de servicios
futuros (facturación, laboratorio, farmacia) se agregan en su turno.

| Rol                  | Descripción                                       |
| -------------------- | ------------------------------------------------- |
| `ROLE_SUPER_ADMIN`   | Administra a los administradores y las claves     |
| `ROLE_ADMIN`         | Gestión de usuarios operativos y configuración    |
| `ROLE_DOCTOR`        | Gestión de atenciones, acceso a historias clínicas |
| `ROLE_NURSE`         | Triage, actualización de estados de atención      |
| `ROLE_RECEPTIONIST`  | Registro de pacientes, creación de atenciones     |
| `ROLE_MEDICAL_RECORDS` | Archivo clínico — copias de la historia para el paciente, sin editarla |

Reglas de administración, aplicadas en el dominio de `auth-service`:

- Solo `SUPER_ADMIN` crea o modifica usuarios `ADMIN` y `SUPER_ADMIN`; `ADMIN` gestiona los roles operativos.
- Nadie cambia su propio rol ni su propio estado.
- Siempre queda al menos un `SUPER_ADMIN` activo: suspender, desactivar o cambiar el rol del último se
  rechaza, también cuando dos administradores lo intentan a la vez (se bloquean sus filas en la base).
- Estados: `PENDING_ACTIVATION` → `ACTIVE` ⇄ `SUSPENDED`, y `DEACTIVATED` reversible; suspender y
  desactivar exigen motivo y registran quién y cuándo. Ningún usuario se borra.
- Suspender, desactivar, cambiar el rol, forzar el cambio de contraseña o resetearla fija
  `tokensNotBefore`: los tokens emitidos antes dejan de valer.

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
como OAuth2 Resource Server (sección 2.6), convierte el rol en la autoridad `ROLE_<ROL>` y cada
controlador aplica `@PreAuthorize`:

```java
@GetMapping("/{uuid}")
@PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'DOCTOR', 'NURSE', 'RECEPTIONIST')")
ResponseEntity<PatientDetailsView> get(@PathVariable UUID uuid) { ... }

@PostMapping("/{uuid}/deactivation")
@PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
ResponseEntity<PatientView> deactivate(@PathVariable UUID uuid, ...) { ... }
```

No se realiza ninguna llamada al Auth Service en tiempo de request: los roles están en el token y la
validación es local. El rol que aplica es el del token vigente; un cambio de rol mueve
`tokensNotBefore` y los tokens anteriores dejan de valer en cuanto llega `auth.users.v1`.

---

## 5. Rate Limiting

`api-gateway` aplica dos límites independientes con contadores de ventana fija en su Redis:

| Clave | Límite | Momento | Motivo |
|---|---|---|---|
| Dirección IP (`clinica:gateway:rate-limit:address:<sha256>`) | 1000 por minuto | Antes de autenticar | Cubre el login y cualquier ruta pública, donde ataca quien no tiene credenciales; umbral alto porque tras un NAT hay muchos usuarios legítimos |
| Usuario de la sesión (`…:user:<sha256 del uuid>`) | 300 por minuto | Después de autenticar | La identidad sale de la sesión del gateway, no de una cabecera; frena la extracción masiva desde una cuenta comprometida |

- El conteo es un script Lua atómico (`INCR` y, en el primer golpe, `PEXPIRE`), correcto con varias
  réplicas del gateway. En el borde de dos ventanas pueden pasar hasta el doble de peticiones en poco
  tiempo; es una limitación aceptada.
- Las respuestas llevan `RateLimit-Limit`, `RateLimit-Remaining` y `RateLimit-Reset`; al superar el límite,
  `429 TOO_MANY_REQUESTS` con `Retry-After`.
- **Si Redis no responde, falla en abierto:** deja pasar y registra el fallo. Una caída de la caché no debe
  tumbar un sistema hospitalario; el frenado de intentos de login de `auth-service` (sección 3) sigue
  activo y vive en MySQL.
- La IP es la dirección de la conexión que llega al gateway. Detrás de un balanceador habría que
  configurar sus direcciones como proxies de confianza.

### 5.6 Escenarios de Protección

| Escenario              | Protección                                    |
| ---------------------- | --------------------------------------------- |
| Fuerza bruta en login  | Rate limit por IP + bloqueo de cuenta tras N fallos |
| Scraping de pacientes  | Rate limit por userId impide extracción masiva |
| DoS desde un usuario   | 429 rápido, sin carga al microservicio        |
| DoS desde IP anónima   | Rate limit por IP aplicado antes de validar JWT |

---

## 6. Auditoría

### 6.1 Identidad del personal

`auth-service` guarda dos registros complementarios:

- **Historial de cada usuario** (Envers, `auth_history`): cada versión con quién la cambió y cuándo.
  El usuario de la aplicación solo puede insertar. Se consulta en `GET /api/v1/users/{uuid}/history`.
- **Eventos de seguridad** en `auth.security-audit.v1`, publicados por outbox en la misma transacción
  que el cambio o el contador de frenado: cambios de cuentas con su actor y motivo, logins completados
  (con `amr` y si fue step-up), fallos de contraseña y segundo factor con el correo intentado, IP y
  navegador, solicitudes de reseteo, desbloqueos, códigos de recuperación regenerados y reutilización
  de refresh tokens. El topic no se compacta ni expira.

Además `auth.users.v1` publica el estado completo de cada usuario (compactado) para que los servicios
corten el acceso de un usuario suspendido sin esperar a que venza su token. Tipos, contratos y ejemplos
en `BackEnd-Clinica/auth-service/events/README.md`. Ningún evento lleva contraseñas, secretos TOTP,
códigos de recuperación ni tokens.

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
  ambas quedan encadenadas. Cada firma calcula el SHA-256 de un JSON canónico, lo sella con la clave
  ECDSA P-256 del motor transit de OpenBao, que nunca sale de él, y lo encadena por paciente. Los
  sellos se verifican localmente con las claves públicas de cada versión. `GET /patients/{uuid}/integrity`
  recalcula la cadena; `clinical.encounters.v1` publica la cabeza para que un tercero pueda detectar
  incluso el truncamiento del final de la cadena.
- **Cifrado extremo del contenido.** Todo el texto clínico se cifra con AES-GCM y una DEK por
  paciente, envuelta en transit por la clave maestra `clinical-kek`, que nunca entra en la base ni en el
  servicio. El AAD ata cada bloque a su propósito y a su registro, y cada envoltura a su paciente.
  Perder el almacenamiento de OpenBao sin respaldo vuelve ilegible la historia: el runbook de rotación y recuperación está en
  `clinical-history-service/docs/claves-y-cifrado.md`.
- **Firmar exige step-up.** Firmar o anular una nota, romper el vidrio y emitir la copia exigen un
  segundo factor verificado hace 5 minutos o menos (`auth_time` y `amr: mfa`); si no, `401
  STEP_UP_REQUIRED` y el gateway pide el código TOTP.
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
  solo se ocupa de identidades. Si OpenBao cae, los servicios que ya arrancaron siguen funcionando;
  en la historia clínica solo se detienen la firma y la lectura de pacientes cuya clave no está en caché.
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

### 8.1 ES256 firmado en transit en lugar de HMAC o de una clave privada en archivo

**Problema:** con HMAC todos los servicios necesitan el secreto que también sirve para emitir tokens; con una clave privada en archivo, comprometer `auth-service` la expone.

**Solución:** la clave ECDSA P-256 vive en OpenBao transit y no se exporta; `auth-service` pide cada firma y los servicios verifican con el JWKS (sección 2).

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
