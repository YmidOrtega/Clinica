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
  {refreshToken}

Auth Service:
  · Marca refresh token como REVOKED en base de datos
  · El access token sigue siendo técnicamente válido hasta expirar
    (corta duración = ventana máxima de 15 min)
  · Registra evento de logout en AuditLog
```

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

El API Gateway implementa un token bucket por usuario usando Redis.

### 5.1 Algoritmo Token Bucket

```
Estado inicial: tokens_disponibles[userId] = CAPACITY (ej. 100)

Por cada request:
  IF tokens_disponibles[userId] > 0:
    DECREMENT tokens_disponibles[userId]
    ALLOW request
  ELSE:
    RETURN 429 Too Many Requests

Recarga (cada segundo):
  tokens_disponibles[userId] = MIN(CAPACITY, tokens + REFILL_RATE)
```

### 5.2 Cabeceras de Respuesta

El gateway incluye información de rate limiting en las respuestas:

```
X-RateLimit-Remaining: 47
X-RateLimit-Limit: 100
X-RateLimit-Reset: 1716134460
```

### 5.3 Escenarios de Protección

| Escenario              | Protección                                    |
| ---------------------- | --------------------------------------------- |
| Fuerza bruta en login  | Rate limit + bloqueo de cuenta tras N fallos  |
| Scraping de pacientes  | Rate limit por userId impide extracción masiva |
| DoS desde un usuario   | 429 rápido, sin carga al microservicio        |
| DoS desde IP anónima   | Rate limit aplicado antes de validar JWT      |

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
