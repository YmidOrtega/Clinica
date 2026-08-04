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

### 4.2 Matriz de Permisos

| Recurso                      | ADMIN | DOCTOR | NURSE | RECEPTIONIST | BILLING |
| ---------------------------- | ----- | ------ | ----- | ------------ | ------- |
| Crear paciente               | ✓     | ✓      | ✗     | ✓            | ✗       |
| Ver historia clínica         | ✓     | ✓      | ✓     | ✗            | ✗       |
| Modificar historia clínica   | ✓     | ✓      | ✗     | ✗            | ✗       |
| Crear atención               | ✓     | ✓      | ✓     | ✓            | ✗       |
| Cambiar estado atención      | ✓     | ✓      | ✓     | ✗            | ✗       |
| Acceder facturación          | ✓     | ✗      | ✗     | ✗            | ✓       |
| Gestionar médicos            | ✓     | ✗      | ✗     | ✗            | ✗       |
| Gestionar usuarios           | ✓     | ✗      | ✗     | ✗            | ✗       |
| Chat con IA                  | ✓     | ✓      | ✓     | ✓            | ✗       |

### 4.3 Implementación

Los roles viajan dentro del JWT. Cada microservicio extrae los roles del claim `roles` y aplica `@PreAuthorize` en los controladores:

```java
@PreAuthorize("hasAnyRole('DOCTOR', 'ADMIN')")
@GetMapping("/{id}")
public ResponseEntity<PatientResponse> getPatient(@PathVariable UUID id) { ... }

@PreAuthorize("hasRole('ADMIN')")
@DeleteMapping("/{id}")
public ResponseEntity<Void> deletePatient(@PathVariable UUID id) { ... }
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

Las credenciales de base de datos y las claves RSA se inyectan vía variables de entorno (`.env`), nunca hardcodeadas en el código fuente.

---

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
