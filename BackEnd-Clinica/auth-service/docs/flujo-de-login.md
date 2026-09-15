# Flujo de login para el frontend

`auth-service` es un servidor OAuth 2.1 / OpenID Connect. El frontend no recibe tokens: el gateway es el
cliente OAuth (patrón BFF) y el navegador solo guarda la cookie de sesión de `auth-service`
(`CLINICA_AUTH_SESSION`, `HttpOnly`, `SameSite=Lax`). Las pantallas de login, activación y reseteo viven
en el frontend y hablan con esta API JSON.

El frontend y `auth-service` deben servirse **desde el mismo origen** a través del gateway (`/auth/**`),
para que la cookie y el token CSRF viajen sin CORS.

## Secuencia

```
Navegador            Gateway (cliente OAuth)          auth-service                  Frontend (Astro)
    │  GET /algo protegido  │                               │                              │
    │──────────────────────►│ 302 /auth/oauth2/authorize    │                              │
    │◄──────────────────────│   ?code_challenge=…&state=…   │                              │
    │  GET /auth/oauth2/authorize ─────────────────────────►│ sin sesión: guarda la        │
    │◄──────────────────────────────── 302 /login ──────────│ petición y redirige          │
    │  GET /login ────────────────────────────────────────────────────────────────────────►│
    │  GET /auth/api/v1/session ───────────────────────────►│ { csrf }                     │
    │  POST /auth/api/v1/login (X-CSRF-TOKEN) ─────────────►│ { SECOND_FACTOR_REQUIRED }   │
    │  POST /auth/api/v1/login/second-factor ──────────────►│ código TOTP verificado en    │
    │◄──────────────────────────────────────────────────────│ OpenBao: { AUTHENTICATED,    │
    │                                                       │   continueUrl }              │
    │  location = continueUrl ─────────────────────────────►│ 302 redirect_uri?code=…      │
    │  GET /login/oauth2/code/clinica?code=… ─►│ POST /oauth2/token (private_key_jwt)      │
    │                       │◄──────────────────────────────│ access (5 min), refresh, id  │
```

## Endpoints

Todas las rutas son relativas a `auth-service` (a través del gateway: `/auth/...`). Los `POST` exigen el
token CSRF de la sesión en la cabecera que indica `GET /api/v1/session`.

### `GET /api/v1/session`

Estado de la sesión y token CSRF. Llamarlo al cargar cada pantalla.

```json
{
  "authenticated": true,
  "user": { "uuid": "57a7fc78-…", "email": "ana@clinica.local", "name": "Ana Rojas", "role": "DOCTOR",
            "authenticatedAt": "2026-09-14T15:04:05.123Z" },
  "pendingStep": null,
  "csrf": { "headerName": "X-CSRF-TOKEN", "token": "…" }
}
```

Con `authenticated: false`, `user` es `null`. `pendingStep` dice qué pantalla mostrar si se recarga a mitad
del flujo: `PASSWORD_CHANGE`, `SECOND_FACTOR`, `SECOND_FACTOR_ENROLLMENT` (sin autenticar) o `STEP_UP`
(autenticada, pero la autorización pidió reautenticarse). Los pasos pendientes del login vencen a los
10 minutos; después hay que volver a `POST /api/v1/login`.

### `POST /api/v1/login`

```json
{ "email": "ana@clinica.local", "password": "una frase larga" }
```

La contraseña sola **nunca** autentica: todo el personal usa un segundo factor TOTP.

| Respuesta | Qué hacer |
|---|---|
| `200 { "outcome": "SECOND_FACTOR_REQUIRED" }` | Pedir el código de 6 dígitos de la aplicación autenticadora o un código de recuperación |
| `200 { "outcome": "SECOND_FACTOR_ENROLLMENT_REQUIRED" }` | Primer login (o segundo factor reiniciado por un administrador): mostrar el enrolamiento |
| `200 { "outcome": "PASSWORD_CHANGE_REQUIRED" }` | Mostrar el formulario de nueva contraseña; después sigue el segundo factor |
| `401 INVALID_CREDENTIALS` | Mensaje genérico. Es igual para correo inexistente, contraseña errada o usuario suspendido |
| `429 TOO_MANY_LOGIN_ATTEMPTS` + `Retry-After: <segundos>` | Deshabilitar el botón durante esa espera |
| `423 ACCOUNT_LOCKED` | Ofrecer "restablecer contraseña" o contactar a un administrador |

### `POST /api/v1/login/password-change`

Solo después de `PASSWORD_CHANGE_REQUIRED`, dentro de los 10 minutos siguientes.

```json
{ "newPassword": "otra frase larga" }
```

`200` con `SECOND_FACTOR_REQUIRED` o `SECOND_FACTOR_ENROLLMENT_REQUIRED`. Errores: `400 PASSWORD_REJECTED`
(ver política), `400 PASSWORD_REUSED`, `409 PASSWORD_CHANGE_NOT_PENDING`.

### `POST /api/v1/login/second-factor/enrollment`

Solo con `SECOND_FACTOR_ENROLLMENT_REQUIRED`. Cuerpo vacío (`{}`).

```json
{ "otpauthUrl": "otpauth://totp/Clinica:ana@clinica.local?secret=…&issuer=Clinica…", "qrPngBase64": "iVBORw0…" }
```

Mostrar el QR (`<img src="data:image/png;base64,…">`) y, para quien no pueda escanearlo, el `secret` de la
URL. El secreto vive en el motor TOTP de OpenBao; `auth-service` no lo guarda. Llamarlo otra vez genera
un secreto nuevo e invalida el anterior. Error: `409 SECOND_FACTOR_NOT_PENDING`.

### `POST /api/v1/login/second-factor/enrollment/confirmation`

```json
{ "code": "123456" }
```

```json
{ "outcome": "AUTHENTICATED", "continueUrl": "…/oauth2/authorize?…",
  "recoveryCodes": ["K7QX-M2PA-…", "…"], "remainingRecoveryCodes": 10 }
```

Los 10 códigos de recuperación **solo se entregan aquí**: mostrarlos con opción de copiar o imprimir y
pedir confirmación antes de navegar a `continueUrl`. Errores: `401 INVALID_SECOND_FACTOR`,
`429 TOO_MANY_LOGIN_ATTEMPTS`, `409 SECOND_FACTOR_NOT_PENDING`.

### `POST /api/v1/login/second-factor`

Solo con `SECOND_FACTOR_REQUIRED`. Uno de los dos campos:

```json
{ "code": "123456" }
```
```json
{ "recoveryCode": "K7QX-M2PA-9TRD-H4EW" }
```

| Respuesta | Qué hacer |
|---|---|
| `200 { "outcome": "AUTHENTICATED", "continueUrl": "…", "remainingRecoveryCodes": 9 }` | Navegar a `continueUrl`. Si quedan pocos códigos, avisar que pida regenerarlos |
| `401 INVALID_SECOND_FACTOR` | Código errado, vencido o ya usado (cada código TOTP sirve una sola vez, aunque siga en su ventana de 30 s) |
| `429 TOO_MANY_LOGIN_ATTEMPTS` + `Retry-After` | Tras 5 fallos seguidos del segundo factor |
| `409 SECOND_FACTOR_NOT_PENDING` | La sesión venció o no pasó por la contraseña: volver al login |

Los códigos de recuperación no distinguen mayúsculas ni guiones y se consumen al usarlos.

### Step-up: reautenticación exigida por el cliente

Una autorización con `max_age` (por ejemplo, antes de firmar una nota clínica) y una sesión autenticada
hace más tiempo que ese valor no emite código: `auth-service` guarda la petición y redirige a
`<login>?step=step-up`. La pantalla pide el segundo factor y llama:

### `POST /api/v1/login/step-up`

Mismo cuerpo y errores que `POST /api/v1/login/second-factor`; exige una sesión autenticada (`401` si no).
Devuelve `continueUrl` con la autorización original y el token nuevo trae `auth_time` actualizado.

Cuando una API responde `401` con `code: STEP_UP_REQUIRED` (por ejemplo, al suspender un usuario), el
gateway repite la autorización con `max_age=300` y el usuario ve esta misma pantalla. La API de usuarios
(`/api/v1/users`, `/api/v1/me`) está en `docs/API_DOCUMENTATION.md`.

### `POST /api/v1/logout`

Cierra la sesión de `auth-service`. `204`. El cierre de la sesión del gateway es parte del BFF.

### `POST /api/v1/activation`

La pantalla de activación recibe `?token=…` en el enlace del correo (válido 72 horas).

```json
{ "token": "…", "password": "una frase larga" }
```

`204`. Errores: `400 PASSWORD_REJECTED`, `400 INVALID_OR_EXPIRED_LINK`. Si la contraseña se rechaza, el
enlace sigue sirviendo para otro intento.

### `POST /api/v1/password-reset/requests`

```json
{ "email": "ana@clinica.local" }
```

Siempre `202`, exista o no el correo. El enlace llega por correo y dura 30 minutos.

### `POST /api/v1/password-reset`

```json
{ "token": "…", "password": "una frase nueva" }
```

`204`: cierra **todas** las sesiones y refresh tokens del usuario y levanta el bloqueo por intentos.
Errores: `400 PASSWORD_REJECTED`, `400 INVALID_OR_EXPIRED_LINK`.

## Política de contraseñas para mostrar en pantalla

- Entre 8 y 128 caracteres; se aceptan espacios, tildes y emojis. No se exigen mayúsculas ni símbolos.
- No puede ser una contraseña común, una repetición o una secuencia (`12345678`, `qwertyui`).
- No puede contener el correo, el nombre de la persona ni el nombre de la clínica.

`400 PASSWORD_REJECTED` trae en `detail` todas las razones separadas por `;`.

## Errores

Todos siguen RFC 9457 (`application/problem+json`) con `code` y `traceId`. Un `403` sin `code` significa
token CSRF ausente o vencido: volver a pedir `GET /api/v1/session`.

## Endpoints OAuth / OIDC (para el gateway)

| Endpoint | Uso |
|---|---|
| `/.well-known/openid-configuration` | Descubrimiento |
| `/oauth2/authorize` | Authorization code con PKCE obligatorio |
| `/oauth2/token` | `authorization_code` y `refresh_token`; el cliente se autentica con `private_key_jwt` (ES256) |
| `/oauth2/jwks` | Claves públicas ES256 (versión activa y anterior de `auth-jwt`) |
| `/oauth2/revoke`, `/oauth2/introspect`, `/userinfo`, `/connect/logout` | Estándar de Spring Authorization Server |

Claims del access token: `iss`, `sub` (uuid del usuario), `aud: clinica-api`, `exp` (5 min), `role`,
`email`, `name`, `auth_time` (último factor verificado), `amr` (`["pwd", "otp", "mfa"]`, o `rec` en lugar
de `otp` si se usó un código de recuperación) y `acr: urn:clinica:acr:mfa`. El refresh token dura 12 horas, rota en cada uso y reutilizar uno
ya rotado revoca toda la sesión.
