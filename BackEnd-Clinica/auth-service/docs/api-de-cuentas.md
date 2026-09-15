# API de cuentas del personal

Todas las rutas exigen `Authorization: Bearer <access token>` de una persona (el gateway lo agrega) y
responden errores RFC 9457 con `code` y `traceId`. Los usuarios se identifican por `uuid`.

## Step-up

Las operaciones marcadas con **step-up** exigen que el último factor se haya verificado hace 5 minutos o
menos (`auth_time`). Si no, responden:

```
401 Unauthorized
WWW-Authenticate: Bearer error="insufficient_user_authentication", max_age=300
{"code": "STEP_UP_REQUIRED", ...}
```

El frontend manda a `GET /bff/step-up?returnTo=<página>` y repite la operación al volver.

## Concurrencia optimista

Las respuestas con un usuario traen `ETag: "<version>"`. Toda modificación sobre `/api/v1/users/{uuid}`
exige `If-Match` con esa versión: sin él responde `428 VERSION_REQUIRED` y con una versión vieja
`412 VERSION_MISMATCH`.

## Cuenta propia — `/api/v1/me`

| Método y ruta | Cuerpo | Respuesta | Notas |
|---|---|---|---|
| `GET /api/v1/me` | — | `200` perfil | `user`, `authenticatedAt`, `methods` (`amr`) y `remainingRecoveryCodes` |
| `PUT /api/v1/me/password` | `{"currentPassword", "newPassword"}` | `204` | **step-up**; aplica la política de contraseñas; las sesiones abiertas siguen vivas |
| `POST /api/v1/me/recovery-codes` | — | `200 {"recoveryCodes": [10]}` | **step-up**; invalida los anteriores; se muestran una sola vez |
| `GET /api/v1/me/sessions` | — | `200` lista de sesiones | ver abajo |
| `DELETE /api/v1/me/sessions/{id}` | — | `204` | cierra una sesión propia; `404 USER_NOT_FOUND` si no existe o no es suya |
| `POST /api/v1/me/session-revocation` | — | `204` | cierra todas, incluida la actual |

### Sesiones

Una sesión es un login con authorization code cuyo refresh token sigue vivo (máximo 5 por persona):

```json
[
  {"id": "3f1c…", "clientId": "api-gateway", "startedAt": "2026-09-15T12:00:00Z",
   "lastRefreshedAt": "2026-09-15T12:20:00Z", "expiresAt": "2026-09-16T00:00:00Z", "current": true}
]
```

`current` marca la sesión del access token de la petición. Cerrar una sesión borra su refresh token: el
dispositivo que la usa sigue con su access token hasta que vence (5 minutos) y luego debe volver a
entrar. Cerrar todas mueve `tokensNotBefore`, así que los access tokens vigentes se rechazan en segundos
en todos los servicios. Queda auditado como `SessionClosed` o `UserSessionsRevoked`.

## Administración — `/api/v1/users`

`SUPER_ADMIN` administra a `ADMIN` y `SUPER_ADMIN`; `ADMIN` a los roles operativos. Nadie cambia su
propio rol ni su propio estado (`403 USER_SELF_MANAGEMENT`) y siempre queda un `SUPER_ADMIN` activo
(`422 LAST_SUPER_ADMIN`).

| Método y ruta | Cuerpo | Respuesta | Step-up |
|---|---|---|---|
| `POST /api/v1/users` | `{"email", "fullName", "role"}` | `201` usuario; envía la invitación | sí |
| `POST /api/v1/users/search?page=0&size=20` | `{"text", "role", "status"}` (todos opcionales) | `200` página de resúmenes; `size` hasta 50 | no |
| `GET /api/v1/users/{uuid}` | — | `200` usuario | no |
| `GET /api/v1/users/{uuid}/history` | — | `200` revisiones con autor | no |
| `PUT /api/v1/users/{uuid}/name` | `{"fullName"}` | `200` usuario | no |
| `PUT /api/v1/users/{uuid}/role` | `{"role"}` | `200` usuario | sí |
| `POST /api/v1/users/{uuid}/suspension` | `{"reason"}` (10 caracteres o más) | `200` usuario | sí |
| `POST /api/v1/users/{uuid}/deactivation` | `{"reason"}` | `200` usuario | sí |
| `POST /api/v1/users/{uuid}/reactivation` | — | `200` usuario | sí |
| `POST /api/v1/users/{uuid}/password-change-requirement` | `{"reason"}` | `200` usuario | no |
| `POST /api/v1/users/{uuid}/second-factor-reset` | `{"reason"}` | `200` usuario; vuelve a enrolar en el siguiente login | sí |
| `POST /api/v1/users/{uuid}/session-revocation` | — | `200` usuario; cierra todas sus sesiones | sí |
| `POST /api/v1/users/{uuid}/invitation` | — | `202`; reenvía la invitación pendiente | no |
| `POST /api/v1/users/{uuid}/unlock` | — | `200` usuario; borra los fallos acumulados de login y de segundo factor | no |

Las rutas con `{uuid}` que modifican (todas salvo `invitation` y `unlock`) exigen `If-Match`.

### Usuario

```json
{
  "uuid": "…", "version": 4, "email": "ana.rojas@clinica.local", "fullName": "Ana Rojas", "role": "NURSE",
  "status": {"code": "SUSPENDED", "reason": "Licencia no remunerada", "changedBy": "…", "changedAt": "…"},
  "credential": {"state": "CURRENT", "changedAt": "…", "reason": null},
  "secondFactor": {"state": "TOTP_ENROLLED", "enrolledAt": "…"},
  "locked": false, "createdAt": "…", "updatedAt": "…"
}
```

`status.code`: `PENDING_ACTIVATION`, `ACTIVE`, `SUSPENDED`, `DEACTIVATED`. `credential.state`: `NOT_SET`,
`CURRENT`, `CHANGE_REQUIRED`. `secondFactor.state`: `NOT_ENROLLED`, `TOTP_ENROLLED`. `locked` indica que la
cuenta superó el límite de fallos y necesita `unlock` o un reseteo de contraseña.

### Códigos de error

| `code` | Estado | Cuándo |
|---|---|---|
| `USER_NOT_FOUND` | 404 | uuid o sesión inexistente |
| `USER_EMAIL_ALREADY_REGISTERED` | 409 | invitación con un correo usado |
| `USER_INVALID_DATA` | 400 | campo inválido; `detail` nombra el campo |
| `USER_INVALID_STATUS_TRANSITION` | 422 | p. ej. suspender a alguien desactivado |
| `USER_ROLE_NOT_MANAGEABLE` | 403 | el rol del usuario está fuera del alcance de quien llama |
| `USER_SELF_MANAGEMENT` | 403 | cambiar el propio rol o estado |
| `LAST_SUPER_ADMIN` | 422 | dejaría a la clínica sin `SUPER_ADMIN` activo |
| `USER_NOT_ACTIVE` | 422 | la operación solo aplica a usuarios activos |
| `SECOND_FACTOR_NOT_ENROLLED` | 422 | resetear un segundo factor que no existe |
| `PASSWORD_REJECTED` | 400 | la nueva contraseña no cumple la política |
| `CURRENT_PASSWORD_INVALID` | 400 | la contraseña actual no coincide |
| `PASSWORD_REUSED` | 400 | la nueva contraseña es igual a la actual |
| `TOO_MANY_LOGIN_ATTEMPTS` | 429 | demasiados fallos de la contraseña actual; trae `Retry-After` |
| `ACCOUNT_LOCKED` | 423 | la cuenta superó el límite duro de fallos |
| `VERSION_REQUIRED` / `VERSION_MISMATCH` | 428 / 412 | ver concurrencia optimista |
| `STEP_UP_REQUIRED` | 401 | ver arriba |
