# Operación de auth-service

Procedimientos para quien opera la plataforma. Los comandos `bao` usan un token con permisos de
operador (en el stack local, `root_token` de `init.json` en el volumen `openbao_bootstrap`) y corren en la
red `secrets-net`:

```bash
docker run --rm -it --user root --network <proyecto>_secrets-net \
  -v <proyecto>_openbao_tls:/openbao/tls:ro -v <proyecto>_openbao_bootstrap:/openbao/bootstrap:ro \
  -e BAO_ADDR=https://openbao:8200 -e BAO_CACERT=/openbao/tls/ca.crt \
  --entrypoint sh clinica/openbao-tools:2.6.2 -c 'export BAO_TOKEN=$(jq -r .root_token /openbao/bootstrap/init.json); sh'
```

## Tiempos que gobiernan cada procedimiento

| Valor | Dónde | Efecto |
|---|---|---|
| Access token 5 min | `clinica.auth.server.tokens.access-token-ttl` | máximo que vive un token ya emitido sin revocación explícita |
| Refresh token 12 h, sesión absoluta 12 h | `clinica.auth.server.tokens.refresh-token-ttl`, `clinica.auth.server.session-absolute-lifetime` | un turno completo sin volver a entrar |
| Metadatos de claves de transit 5 min | `clinica.openbao.transit.key-refresh-interval` | cuánto tarda `auth-service` en firmar con una versión nueva |
| Caché del JWKS 5 min, tolerancia 24 h | `clinica-commons-security` | cuánto tardan los servicios en dejar de aceptar una clave retirada; cuánto validan si auth cae |
| `auth.users.v1` en segundos | `AuthUsersTopicReader` | cuánto tarda un servicio en rechazar a un usuario suspendido o con sesiones cerradas |

## Rotación de claves

### Firma de tokens (`transit/auth-jwt`)

Rota sola cada 30 días (`auto_rotate_period=720h`). Para adelantarla:

```bash
bao write -f transit/keys/auth-jwt/rotate
```

No hay que reiniciar nada. En 5 minutos `auth-service` firma con la versión nueva; el JWKS publica la
nueva y la anterior, así que los tokens vigentes siguen validando. Los servicios traen la clave nueva la
primera vez que ven su `kid`.

### Clave comprometida

Si la versión activa pudo filtrarse, además de rotar hay que retirarla del JWKS:

```bash
bao write -f transit/keys/auth-jwt/rotate
bao read -field=latest_version transit/keys/auth-jwt
bao write transit/keys/auth-jwt/config min_decryption_version=<latest_version>
```

`auth-service` deja de publicar las versiones menores en 5 minutos y los servicios dejan de aceptarlas al
refrescar su caché (otros 5 minutos). Todas las sesiones siguen vivas: el refresh token es opaco y el
siguiente refresh entrega un access token firmado con la versión nueva.

### Aserciones de los clientes (`transit/api-gateway-client`, `patient-service-client`, `clinical-history-service-client`)

```bash
bao write -f transit/keys/<cliente>/rotate
```

El cliente firma con la versión nueva cuando refresca sus metadatos y `auth-service` consulta transit en
cuanto ve un `kid` desconocido; no hay ventana de rechazo. Si la clave se comprometió, fijar
`min_decryption_version` como arriba.

### Credenciales de base de datos y AppRoles

Ver `platform/openbao/README.md`.

## Personal

### Desbloquear una cuenta

Tras 100 fallos consecutivos de contraseña la cuenta queda bloqueada (`locked: true` en el usuario). Las
esperas por dirección y por cuenta (hasta 15 min y 1 min) vencen solas.

- Un `ADMIN` (o `SUPER_ADMIN` para administradores) llama `POST /api/v1/users/{uuid}/unlock`, que borra
  los fallos de contraseña y de segundo factor y queda auditado como `SignInUnlocked`.
- La persona también se desbloquea sola reseteando la contraseña por correo.

### Perdió el teléfono del segundo factor

1. Si conserva códigos de recuperación, entra con uno y sigue trabajando; conviene regenerarlos.
2. Quien administra su rol llama `POST /api/v1/users/{uuid}/second-factor-reset` con motivo. Se borra su
   clave `totp/keys/staff-<uuid>`, se revocan sus códigos y sesiones, y en el siguiente login enrola de
   nuevo.

### Sesión robada o dispositivo perdido

- La persona lista sus sesiones y cierra la del dispositivo (`DELETE /api/v1/me/sessions/{id}`), o cierra
  todas (`POST /api/v1/me/session-revocation`).
- Un administrador cierra todas las de un usuario con step-up
  (`POST /api/v1/users/{uuid}/session-revocation`).
- Cerrar todas mueve `tokensNotBefore`: los access tokens vigentes se rechazan en segundos en todos los
  servicios. Cerrar una sola borra su refresh token y su access token vive hasta 5 minutos.
- Si alguien reutiliza un refresh token ya rotado, `auth-service` revoca la sesión completa sola y
  registra `RefreshTokenReuseDetected` en `auth.security-audit.v1`.
- Un login en el mismo segundo que la revocación se rechaza (`iat` tiene resolución de segundos); la
  persona repite el login.

### Recuperar el acceso de SUPER_ADMIN

Mantener siempre al menos dos `SUPER_ADMIN` activos: con dos, cualquier pérdida se resuelve por la API
(reseteo de segundo factor, requerimiento de cambio de contraseña, reactivación).

Si el único `SUPER_ADMIN` perdió contraseña, teléfono y códigos, no hay camino por la API. Procedimiento
de emergencia, con dos personas presentes y un acta:

1. Desactivar la cuenta directamente en `auth-db` (el cambio no pasa por el dominio: no genera historial
   Envers ni evento, por eso va al acta):

   ```sql
   UPDATE users SET status = 'DEACTIVATED', status_reason = 'Recuperación de emergencia, acta <número>',
       status_changed_by = uuid, status_changed_by_role = 'SUPER_ADMIN', status_changed_at = NOW(6),
       tokens_not_before = NOW(6), version = version + 1
   WHERE uuid = '<uuid>';
   ```

2. Poner un correo nuevo de arranque y reiniciar `auth-service`:

   ```bash
   bao kv patch secret/auth/bootstrap super-admin-email=<correo nuevo> super-admin-name="<nombre>"
   docker compose -p <proyecto> restart auth-service
   ```

   Al arrancar sin ningún `SUPER_ADMIN` no desactivado, invita a ese correo. Si el correo ya pertenece a
   otro usuario no invita a nadie y lo deja en el log.

3. La persona nueva activa la cuenta y enrola TOTP. Por la API reactiva la cuenta anterior y le resetea
   el segundo factor, o la deja desactivada; esas acciones sí quedan auditadas.

## OpenBao no disponible

- Sin OpenBao `auth-service` no firma tokens ni valida TOTP: los endpoints OAuth responden
  `503 {"error": "temporarily_unavailable"}` y la API de login y de cuentas `503 AUTH_KEYS_UNAVAILABLE`,
  ambos con `Retry-After: 5`. El login con contraseña sigue funcionando hasta el segundo factor.
- Los access tokens emitidos siguen validando en todos los servicios hasta que vencen; el JWKS en caché
  sirve 24 horas aunque `auth-service` también caiga.
- Al volver el clúster no hay que hacer nada: `openbao-e2e.sh` comprueba la recuperación.

## Auditoría

`auth.security-audit.v1` (retención infinita) registra logins, fallos, desbloqueos, códigos de
recuperación, sesiones cerradas y reutilización de refresh tokens; `auth.users.v1` (compactado) trae el
estado completo de cada usuario tras cada cambio. Esquemas en `auth-service/events/`.
