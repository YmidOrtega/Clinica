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
    │  GET /login ─────────────────────────────────────────────────────────────────────────►│
    │  GET /auth/api/v1/session ───────────────────────────►│ { csrf }                     │
    │  POST /auth/api/v1/login (X-CSRF-TOKEN) ─────────────►│ { AUTHENTICATED,             │
    │◄──────────────────────────────────────────────────────│   continueUrl }              │
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
  "user": { "uuid": "57a7fc78-…", "email": "ana@clinica.local", "name": "Ana Rojas", "role": "DOCTOR" },
  "csrf": { "headerName": "X-CSRF-TOKEN", "token": "…" }
}
```

Con `authenticated: false`, `user` es `null`.

### `POST /api/v1/login`

```json
{ "email": "ana@clinica.local", "password": "una frase larga" }
```

| Respuesta | Qué hacer |
|---|---|
| `200 { "outcome": "AUTHENTICATED", "continueUrl": "…/oauth2/authorize?…" }` | Navegar a `continueUrl` (`window.location`). Si el login no vino de una autorización, apunta a la página de inicio |
| `200 { "outcome": "PASSWORD_CHANGE_REQUIRED" }` | Mostrar el formulario de nueva contraseña; la sesión todavía no está autenticada |
| `401 INVALID_CREDENTIALS` | Mensaje genérico. Es igual para correo inexistente, contraseña errada o usuario suspendido |
| `429 TOO_MANY_LOGIN_ATTEMPTS` + `Retry-After: <segundos>` | Deshabilitar el botón durante esa espera |
| `423 ACCOUNT_LOCKED` | Ofrecer "restablecer contraseña" o contactar a un administrador |

### `POST /api/v1/login/password-change`

Solo después de `PASSWORD_CHANGE_REQUIRED`, dentro de los 10 minutos siguientes.

```json
{ "newPassword": "otra frase larga" }
```

`200` con la misma forma que `AUTHENTICATED`. Errores: `400 PASSWORD_REJECTED` (ver política),
`400 PASSWORD_REUSED`, `409 PASSWORD_CHANGE_NOT_PENDING`.

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
`email`, `name`, `auth_time` y `amr`. El refresh token dura 12 horas, rota en cada uso y reutilizar uno
ya rotado revoca toda la sesión.
