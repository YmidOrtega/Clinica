# Gateway BFF para el frontend

`api-gateway` es el único punto de entrada del navegador. Es el cliente OAuth de `auth-service` (patrón
*backend for frontend*): hace el login por authorization code con PKCE, guarda los tokens en su Redis y
los agrega a cada llamada a la API. **El navegador nunca ve un token**; solo tiene cookies `HttpOnly`.

El frontend vive en otro origen (en desarrollo `http://localhost:4321`) y llama al gateway con CORS y
credenciales:

```js
const api = (path, options = {}) =>
  fetch(`${GATEWAY}${path}`, { credentials: 'include', ...options });
```

## Sesión

### `GET /bff/session`

Llamarlo al cargar la aplicación y después de cada navegación de login, step-up o logout.

```json
{
  "authenticated": true,
  "user": { "uuid": "57a7fc78-…", "email": "ana@clinica.local", "name": "Ana Rojas", "role": "DOCTOR",
            "authenticatedAt": "2026-09-15T14:30:05Z", "methods": ["pwd", "otp", "mfa"] },
  "csrf": { "headerName": "X-CSRF-TOKEN", "token": "…" },
  "loginUrl": "/bff/login",
  "stepUpUrl": "/bff/step-up",
  "logoutUrl": "/bff/logout"
}
```

Con `authenticated: false`, `user` es `null`. El token CSRF va en la cabecera `csrf.headerName` de todo
`POST`, `PUT`, `PATCH` y `DELETE` hacia `/api/**` y `/bff/logout`; sin él la respuesta es
`403 CSRF_TOKEN_INVALID`. Como el frontend está en otro origen no puede leer la cookie, por eso el token
llega en este JSON.

### Login

Navegación completa (no `fetch`) a:

```
GET {GATEWAY}/bff/login?returnTo=http://localhost:4321/pacientes
```

El gateway redirige a `auth-service`, que manda a la pantalla de login del frontend
(`auth-service/docs/flujo-de-login.md`). Al terminar, el navegador vuelve a `returnTo`. Solo se aceptan
URLs de los orígenes configurados en `GATEWAY_FRONTEND_ORIGINS`; cualquier otra vuelve a la página de
inicio. Si el login falla se vuelve a la página de inicio con `?error=login`.

Las pantallas de login llaman a `auth-service` a través del gateway en `{GATEWAY}/auth/api/v1/...`,
también con `credentials: 'include'`.

### Respuestas de la API que el frontend debe manejar

| Respuesta | Qué hacer |
|---|---|
| `401 UNAUTHENTICATED` | No hay sesión: navegar a `loginUrl?returnTo=<página actual>` |
| `401 SESSION_EXPIRED` | `auth-service` ya no renueva la sesión (12 h, revocada o usuario suspendido): igual que el anterior |
| `401 STEP_UP_REQUIRED` (con `WWW-Authenticate: … insufficient_user_authentication`) | La operación exige un segundo factor de hace 5 minutos o menos: navegar a `stepUpUrl?returnTo=<página actual>` y repetir la acción al volver |
| `403 CSRF_TOKEN_INVALID` | Pedir de nuevo `GET /bff/session` y reintentar |
| `429 TOO_MANY_REQUESTS` + `Retry-After` | Esperar esos segundos |
| `503 AUTH_UNAVAILABLE` | `auth-service` no pudo renovar el access token; reintentar en unos segundos |
| `503 SERVICE_UNAVAILABLE` | El servicio destino no responde |

### Step-up

```
GET {GATEWAY}/bff/step-up?returnTo=http://localhost:4321/atenciones/…/firmar
```

Igual que el login, pero pide a `auth-service` `max_age=300`. Si la última verificación del segundo
factor es más antigua, la pantalla de login recibe `?step=step-up` y solo pide el código TOTP.

### Logout

```js
const { endSessionUrl } = await (await api('/bff/logout', { method: 'POST', headers: { [csrf.headerName]: csrf.token } })).json();
window.location.assign(endSessionUrl);
```

El gateway revoca el refresh token en `auth-service`, borra sus tokens y la sesión. `endSessionUrl`
cierra además la sesión de `auth-service` y vuelve a la página de inicio del frontend.

## Rutas

| Ruta del gateway | Destino | Credenciales hacia el servicio |
|---|---|---|
| `/auth/**` | `auth-service` sin el prefijo | Su propia cookie `CLINICA_AUTH_SESSION`; el gateway quita su cookie de sesión y agrega `X-Forwarded-Prefix: /auth` |
| `/api/v1/users/**`, `/api/v1/me/**` | `auth-service` | `Authorization: Bearer <access token>` |
| `/api/v1/patients/**`, `/api/v1/unidentified-patients/**` | `patient-service` | ídem |
| `/api/v1/clinical/**` | `clinical-history-service` | ídem |

Hacia los servicios el gateway quita `Cookie`, las cabeceras CSRF y cualquier `Authorization` o
`X-Forwarded-*` que envíe el navegador. Los servicios aún no migrados no tienen ruta.

## Cómo funciona por dentro

- **Sesión:** Spring Session en Redis (`CLINICA_SESSION`, `HttpOnly`, `SameSite=Lax` por defecto,
  `Secure` en producción). 15 minutos sin actividad y 12 horas desde el login como máximo.
- **Tokens:** en Redis bajo una clave aleatoria guardada en la sesión, nunca en la cookie. El gateway se
  autentica ante `auth-service` con `private_key_jwt` firmado en OpenBao transit (`api-gateway-client`).
- **Renovación sin reutilizar refresh tokens:** el access token se renueva 30 segundos antes de vencer.
  Un candado en Redis por sesión garantiza que solo una petición lo renueve; las demás esperan el token
  nuevo. Sin esto, dos peticiones en paralelo usarían el mismo refresh token y `auth-service`, que
  detecta la reutilización, revocaría toda la sesión.
- **Fallos:** si `auth-service` no responde al renovar, el gateway sigue usando el token vigente mientras
  no venza y después responde `503 AUTH_UNAVAILABLE` sin cerrar la sesión. Si rechaza el refresh token,
  cierra la sesión (`401 SESSION_EXPIRED`).
- **Rate limit:** ventana fija en Redis por dirección IP (1000 por minuto, antes de autenticar) y por
  usuario (300 por minuto). Si Redis no responde, deja pasar y lo registra. Cabeceras `RateLimit-Limit`,
  `RateLimit-Remaining` y `RateLimit-Reset`.
- **Cookies entre orígenes:** con `SameSite=Lax` la cookie viaja mientras frontend y gateway compartan
  sitio (`localhost` en desarrollo, subdominios del mismo dominio en producción). Con dominios
  distintos hay que usar `GATEWAY_SESSION_SAME_SITE=none` y `GATEWAY_SESSION_COOKIE_SECURE=true`.

Lo verifican `GatewayBffIT` y `RateLimitIT` (Redis real, OpenBao y `auth-service` simulado) y
`platform/e2e/gateway-e2e.sh` contra `auth-service` y `patient-service` reales.
