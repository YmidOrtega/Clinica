# Eventos de identidad

`auth-service` publica dos topics con el mismo outbox y conector (`debezium/auth-outbox.json`):

- `auth.users.v1`: el estado actual de cada usuario del personal. Los demás servicios lo usan para
  invalidar tokens.
- `auth.security-audit.v1`: quién hizo qué con las cuentas, y cada login, fallo y anomalía.

Ninguno lleva contraseñas, hashes, secretos TOTP, códigos de recuperación ni tokens.

## Cómo se publican

```
User (agregado) ── pullEvents ──► JpaUsers.save ── misma transacción ──► users + auth_outbox.outbox_events
LoginFlow, SecondFactorFlow, … ── SecurityAuditLog ─────────────────────► auth_outbox.outbox_events
                                                                                  │ binlog
                                                                                  ▼
                                                  Debezium (Outbox Event Router) ──► auth.users.v1
                                                                                  └─► auth.security-audit.v1
```

1. `User` registra un evento de dominio en cada cambio. `JpaUsers.save` guarda al usuario y escribe sus
   eventos en `auth_outbox.outbox_events` en la **misma transacción**. Ningún caso de uso puede cambiar
   un usuario sin publicar el evento.
2. Los eventos que no cambian un usuario (logins, fallos, reutilización de refresh tokens) se escriben
   por el puerto `SecurityAuditLog`, dentro de la transacción del caso de uso cuando la hay: un fallo
   de login se guarda junto con su contador de frenado.
3. Debezium lee el binlog y enruta por `aggregatetype`. `auth-service` no usa Kafka y no depende de que
   Kafka o Connect estén arriba.
4. Las filas del outbox se purgan a los 7 días (`clinica.auth.outbox.retention`).

## Estado de usuarios — `auth.users.v1`

| Propiedad | Valor |
|---|---|
| Clave | `userUuid` (orden garantizado por usuario) |
| Particiones | 3 |
| Limpieza | `compact`: Kafka conserva al menos el último estado de cada usuario |
| Cabecera | `eventType` |
| Contrato | [`auth.users.v1.schema.json`](auth.users.v1.schema.json) |

| Tipo | Cuándo |
|---|---|
| `UserInvited` | Invitación o primer `SUPER_ADMIN` |
| `UserActivated` | Activación con el enlace del correo |
| `UserRenamed`, `UserRoleChanged` | Cambio de nombre o rol |
| `UserSuspended`, `UserDeactivated`, `UserReactivated` | Cambio de estado |
| `UserPasswordChangeRequired`, `UserPasswordReset` | Cambio exigido por un administrador o reseteo por correo |
| `UserSecondFactorReset` | Reinicio del segundo factor por un administrador |
| `UserSessionsRevoked` | La persona cerró todas sus sesiones |

Cada evento trae el estado completo, así que un consumidor solo necesita el último por clave:

```json
{
  "eventId": "7c1e5d8a-3b2f-4e6a-9d0c-1f2e3a4b5c6d",
  "type": "UserSuspended",
  "schemaVersion": 1,
  "occurredAt": "2026-09-15T10:32:40.736512Z",
  "traceId": "6aa91ec8b9944ce75be07af6311b3661",
  "userUuid": "3a9c0a35-aa51-4736-949b-b89873cc05f2",
  "userVersion": 4,
  "data": {
    "user": {
      "uuid": "3a9c0a35-aa51-4736-949b-b89873cc05f2",
      "email": "ana@clinica.local",
      "fullName": "Ana Rojas",
      "role": "NURSE",
      "status": "SUSPENDED",
      "tokensNotBefore": "2026-09-15T10:32:40.736512Z"
    }
  }
}
```

Regla para los servicios (la aplica `clinica-commons-security`): rechazar un access token cuyo `iat`
sea anterior a `tokensNotBefore`, o cuyo usuario no esté `ACTIVE`. Así una suspensión corta el acceso
en segundos y no a los 5 minutos del vencimiento del token.

## Auditoría de seguridad — `auth.security-audit.v1`

| Propiedad | Valor |
|---|---|
| Clave | UUID del usuario afectado; `eventId` si no se conoce (correo inexistente) |
| Particiones | 3 |
| Limpieza | `delete` con `retention.ms = -1`: nunca se borra ni se compacta |
| Cabecera | `eventType` |
| Contrato | [`auth.security-audit.v1.schema.json`](auth.security-audit.v1.schema.json) |

| Tipo | `actor` | `details` |
|---|---|---|
| Los de `auth.users.v1` | Administrador que actuó, la propia persona o ninguno (primer `SUPER_ADMIN`) | `reason`, `role`, `previousRole` según el caso |
| `UserPasswordChanged`, `UserSecondFactorEnrolled` | La propia persona | — |
| `SignInCompleted` | — | `methods` (`amr`), `stepUp`, `remainingRecoveryCodes` |
| `SignInFailed` (`outcome: FAILED`) | — | `stage` (`PASSWORD`, `SECOND_FACTOR`, `CURRENT_PASSWORD`), `failureReason` |
| `PasswordResetRequested` | — | — |
| `RecoveryCodesRegenerated` | — | — |
| `SignInUnlocked`, `InvitationResent` | Administrador | — |
| `RefreshTokenReuseDetected` (`outcome: FAILED`) | — | `authorizationId` revocada |

Todos llevan `client` (`address`, `userAgent`) cuando nacen de una petición HTTP. En `SignInFailed` y
`PasswordResetRequested`, `subject.email` es el correo **tal como se intentó**, exista o no.

```json
{
  "eventId": "393de410-60dc-449f-b747-324b0305941b",
  "type": "SignInFailed",
  "schemaVersion": 1,
  "occurredAt": "2026-09-15T10:32:41.120034Z",
  "traceId": "6aa91ec884637b301537c6fc15d20611",
  "outcome": "FAILED",
  "subject": { "uuid": "0a631767-9d23-42f2-bc0c-fa451c6ed96d", "email": "ana@clinica.local" },
  "client": { "address": "10.0.4.12", "userAgent": "Mozilla/5.0 …" },
  "details": { "stage": "PASSWORD", "failureReason": "INVALID_CREDENTIALS" }
}
```

El topic contiene datos personales del personal (correos, direcciones IP) y debe leerse solo desde el
futuro `audit-service`.

## Evolución

- Agregar un campo opcional o un tipo nuevo no cambia la versión; los consumidores ignoran lo que no
  conocen.
- Quitar o renombrar un campo, o cambiar su significado, exige `auth.users.v2` publicado en paralelo.
- `IdentityEventsIT` valida contra los esquemas cada evento que el servicio guarda en el outbox.
