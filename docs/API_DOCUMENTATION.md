# Referencia de API REST — Clínica

**Versión:** 1.0  
**Base URL:** `http://localhost:8080` (API Gateway)  
**Autenticación:** Bearer JWT (RSA-256) en header `Authorization`

---

## Convenciones

- El navegador llama siempre a `api-gateway` con `credentials: 'include'`: la sesión es una cookie del
  gateway, que agrega el access token hacia cada servicio (`BackEnd-Clinica/api-gateway/docs/bff.md`).
  Los servicios, detrás del gateway, exigen `Authorization: Bearer <token>`.
- Toda escritura desde el navegador lleva el token CSRF de `GET /bff/session`.
- Las respuestas exitosas devuelven `2xx`; los errores siguen el formato estándar de Spring.
- Los UUIDs se expresan como strings en formato `xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx`.
- Los timestamps usan ISO 8601: `2025-05-19T14:30:00`.
- Soft delete: los registros eliminados tienen `deletedAt` no nulo y no aparecen en listados normales.

---

## 0. Gateway BFF — `/bff`

| Método y ruta | Uso |
|---|---|
| `GET /bff/session` | ¿Hay sesión?, usuario (`uuid`, `email`, `name`, `role`, `authenticatedAt`, `methods`) y token CSRF |
| `GET /bff/login?returnTo=` | Navegación: inicia el login en `auth-service` y vuelve a `returnTo` (solo orígenes del frontend) |
| `GET /bff/step-up?returnTo=` | Navegación: igual, pidiendo segundo factor de hace 5 minutos o menos |
| `POST /bff/logout` | Revoca el refresh token y devuelve `{ endSessionUrl }` para cerrar también la sesión de auth |

Errores comunes a toda la API a través del gateway: `401 UNAUTHENTICATED` o `SESSION_EXPIRED` (ir a
login), `401 STEP_UP_REQUIRED` (ir a step-up), `403 CSRF_TOKEN_INVALID`, `429 TOO_MANY_REQUESTS` con
`Retry-After`, `503 AUTH_UNAVAILABLE` y `503 SERVICE_UNAVAILABLE`.

---

## 1. Auth Service

`auth-service` es un servidor OAuth 2.1 / OIDC. El frontend no maneja tokens: habla con la API JSON del
flujo de login y el gateway obtiene los tokens como cliente OAuth. Contrato completo, secuencia y errores
en [`BackEnd-Clinica/auth-service/docs/flujo-de-login.md`](../BackEnd-Clinica/auth-service/docs/flujo-de-login.md).

| Método y ruta | Uso |
|---|---|
| `GET /api/v1/session` | Estado de la sesión y token CSRF |
| `POST /api/v1/login` | Correo y contraseña → `SECOND_FACTOR_REQUIRED`, `SECOND_FACTOR_ENROLLMENT_REQUIRED` o `PASSWORD_CHANGE_REQUIRED` |
| `POST /api/v1/login/password-change` | Nueva contraseña cuando se exige cambio |
| `POST /api/v1/login/second-factor/enrollment` · `…/enrollment/confirmation` | QR del TOTP y confirmación con el primer código → `AUTHENTICATED` y 10 códigos de recuperación |
| `POST /api/v1/login/second-factor` | Código TOTP o de recuperación → `AUTHENTICATED` con `continueUrl` |
| `POST /api/v1/login/step-up` | Reautenticación con el segundo factor cuando la autorización pide `max_age` |
| `POST /api/v1/logout` | Cierra la sesión |
| `POST /api/v1/activation` | Activa la cuenta con el enlace del correo |
| `POST /api/v1/password-reset/requests` · `POST /api/v1/password-reset` | Solicita y aplica el reseteo |
| `/oauth2/authorize`, `/oauth2/token`, `/oauth2/jwks`, `/.well-known/openid-configuration` | OAuth 2.1 / OIDC para el gateway |

### 1.1 Usuarios — `/api/v1/users` y `/api/v1/me`

Estas rutas se llaman con el **access token** (`Authorization: Bearer`) que el gateway reenvía, no con
la cookie de sesión. En cada petición `auth-service` vuelve a leer al usuario: si fue suspendido,
desactivado o sus sesiones se revocaron después de emitir el token, responde `401` aunque el token no
haya vencido, y el rol que aplica es el actual, no el del token.

**Convenciones:** usuarios por `uuid`; toda modificación exige `If-Match` con el `ETag` (`428` sin
cabecera, `412` con versión vieja); errores RFC 9457 con `code`.

**Step-up:** las operaciones marcadas exigen un segundo factor verificado hace 5 minutos o menos. Si no,
responden:

```http
HTTP/1.1 401 Unauthorized
WWW-Authenticate: Bearer error="insufficient_user_authentication", error_description="…", max_age=300
Content-Type: application/problem+json

{ "code": "STEP_UP_REQUIRED", "maxAge": 300, "detail": "Confirma tu segundo factor para continuar con esta operación" }
```

El gateway reinicia la autorización con `max_age=300`; `auth-service` pide el código TOTP y el token
nuevo trae `auth_time` actualizado (ver `flujo-de-login.md`).

| Método y ruta (`/api/v1/users`) | Uso | Step-up |
|---|---|:---:|
| `POST /` | Invita `{ email, fullName, role }` → `201` y correo de activación | ✓ |
| `POST /search?page=0&size=20` | `{ text, role, status }` todos opcionales; `text` es prefijo de nombre o correo. Tamaño máximo 50 | |
| `GET /{uuid}` | Usuario con estado, credencial, segundo factor y `locked` (bloqueado por 100 fallos) | |
| `GET /{uuid}/history` | Revisiones (Envers) con quién y cuándo | |
| `PUT /{uuid}/name` | `{ fullName }` | |
| `PUT /{uuid}/role` | `{ role }`; cierra las sesiones del usuario | ✓ |
| `POST /{uuid}/suspension` · `/deactivation` | `{ reason }` (10 a 500 caracteres); cierra sus sesiones | ✓ |
| `POST /{uuid}/reactivation` | Sin cuerpo | ✓ |
| `POST /{uuid}/password-change-requirement` | `{ reason }`: en el siguiente login debe cambiar la contraseña; cierra sus sesiones | |
| `POST /{uuid}/second-factor-reset` | `{ reason }`: borra su TOTP de OpenBao, revoca sus códigos de recuperación y cierra sus sesiones | ✓ |
| `POST /{uuid}/invitation` | Reenvía el correo de activación de un usuario pendiente → `202` | |
| `POST /{uuid}/unlock` | Levanta el bloqueo por intentos fallidos | |

Roles: `SUPER_ADMIN` administra a todos; `ADMIN` solo a los roles operativos (`403 USER_ROLE_NOT_MANAGEABLE`).
Nadie se administra a sí mismo (`403 USER_SELF_MANAGEMENT`) y nunca puede quedar la clínica sin un
`SUPER_ADMIN` activo (`422 LAST_SUPER_ADMIN`).

```json
{
  "uuid": "57a7fc78-…", "version": 3, "email": "ana@clinica.local", "fullName": "Ana Rojas", "role": "DOCTOR",
  "status": { "code": "SUSPENDED", "reason": "Revisión de accesos", "changedBy": "0b9e…", "changedAt": "2026-09-14T15:04:05Z" },
  "credential": { "state": "CURRENT", "changedAt": "2026-09-01T12:00:00Z", "reason": null },
  "secondFactor": { "state": "TOTP_ENROLLED", "enrolledAt": "2026-09-01T12:01:00Z" },
  "locked": false, "createdAt": "2026-09-01T11:58:00Z", "updatedAt": "2026-09-14T15:04:05Z"
}
```

| Método y ruta (`/api/v1/me`) | Uso | Step-up |
|---|---|:---:|
| `GET /` | Mi usuario, `authenticatedAt`, `methods` (`amr`) y `remainingRecoveryCodes` | |
| `PUT /password` | `{ currentPassword, newPassword }` → `204`; `400 CURRENT_PASSWORD_INVALID` (con frenado), `400 PASSWORD_REJECTED`, `400 PASSWORD_REUSED` | |
| `POST /recovery-codes` | Genera 10 códigos nuevos e invalida los anteriores → `{ recoveryCodes }` | ✓ |
| `POST /session-revocation` | Cierra todas mis sesiones y refresh tokens → `204` | |

---

## 2. Patient Service — `/api/v1/patients`

Registro administrativo del paciente: identidad, contacto, afiliación, residencia y estado. La historia
clínica (alergias, enfermedades crónicas, medicamentos, antecedentes, vacunas) pertenece a
`clinical-history-service`.

**Convenciones del servicio:**

- El paciente se identifica por `uuid`. El número de documento nunca va en la URL.
- Toda modificación exige `If-Match` con la versión recibida en el `ETag`. Sin cabecera: `428`;
  con versión desactualizada: `412`.
- Los errores siguen RFC 9457 (`application/problem+json`) con `code` estable y `traceId`.

**Roles:**

| Operación                                        | Roles                                   |
| ------------------------------------------------ | --------------------------------------- |
| Consultar y buscar                               | SUPER_ADMIN, ADMIN, DOCTOR, NURSE, RECEPTIONIST |
| Registrar y actualizar datos                     | SUPER_ADMIN, ADMIN, RECEPTIONIST        |
| Desactivar y reactivar                           | SUPER_ADMIN, ADMIN                      |
| Registrar fallecimiento                          | SUPER_ADMIN, ADMIN, DOCTOR              |
| Historial de cambios                             | SUPER_ADMIN, ADMIN                      |

### POST `/`

Registra un paciente. Si tiene aseguradora, se valida contra `clients-service`: si no existe responde
`422 HEALTH_PROVIDER_NOT_FOUND` y si no es posible validarla, `503 HEALTH_PROVIDER_UNAVAILABLE`.

**Request:**
```json
{
  "document": { "type": "CEDULA_DE_CIUDADANIA", "number": "1098765432" },
  "demographics": {
    "firstNames": "Ana María", "lastNames": "Restrepo Gómez", "birthDate": "1990-04-12",
    "sex": "FEMALE", "countryOfOrigin": "CO", "disability": "NONE"
  },
  "contact": { "mobile": "3001234567", "phone": null, "email": "ana@example.com" },
  "emergencyContact": { "fullName": "Luis Restrepo", "relationship": "FATHER", "phone": "3017654321" },
  "affiliation": { "regime": "CONTRIBUTORY", "affiliateType": "HOLDER", "healthProviderNit": "900123456-7", "policyNumber": null },
  "residence": { "department": "Santander", "municipality": "Bucaramanga", "zone": "URBAN", "address": "Calle 45 # 27-10" }
}
```

Reglas: el tipo de documento debe corresponder a la edad (Registro Civil < 7, Tarjeta de Identidad
7–17, Cédula ≥ 18); los menores requieren `emergencyContact`; `UNINSURED` no admite aseguradora.

**Response `201 Created`** con `Location: /api/v1/patients/{uuid}` y `ETag: "0"`:
```json
{
  "uuid": "034820b2-cffa-4360-b384-09658f31bf9c",
  "version": 0,
  "document": { "type": "CEDULA_DE_CIUDADANIA", "number": "1098765432" },
  "demographics": { "firstNames": "Ana María", "lastNames": "Restrepo Gómez", "birthDate": "1990-04-12", "sex": "FEMALE", "countryOfOrigin": "CO", "disability": "NONE" },
  "contact": { "mobile": "3001234567", "email": "ana@example.com" },
  "emergencyContact": { "fullName": "Luis Restrepo", "relationship": "FATHER", "phone": "3017654321" },
  "affiliation": { "regime": "CONTRIBUTORY", "affiliateType": "HOLDER", "healthProviderNit": "900123456-7" },
  "residence": { "department": "Santander", "municipality": "Bucaramanga", "zone": "URBAN", "address": "Calle 45 # 27-10" },
  "status": { "code": "ACTIVE", "changedAt": "2026-09-13T18:45:46Z" },
  "audit": { "createdAt": "2026-09-13T18:45:46Z", "createdBy": "7d1c3f2e-…", "updatedAt": "2026-09-13T18:45:46Z", "updatedBy": "7d1c3f2e-…" }
}
```

---

### GET `/{uuid}`

Retorna el paciente y la disponibilidad de su aseguradora. Si `clients-service` falla, el paciente se
entrega igual con el último valor conocido de la aseguradora o `availability: UNAVAILABLE`.

**Response `200 OK`** con `ETag`: el mismo cuerpo de `POST /` más:
```json
{
  "healthProvider": { "availability": "AVAILABLE", "name": "Salud Total EPS S.A.", "type": "EPS" }
}
```

`availability`: `AVAILABLE`, `NOT_FOUND`, `UNAVAILABLE`, `NOT_AFFILIATED`.

---

### POST `/search?page=0&size=20`

Busca por documento exacto o por prefijo de apellidos (y opcionalmente nombres), sin distinguir
mayúsculas ni tildes. Usa `POST` para que los datos personales no queden en URLs ni logs de acceso.
`size` máximo: 50.

**Request (documento):**
```json
{ "document": { "type": "CEDULA_DE_CIUDADANIA", "number": "1098765432" } }
```

**Request (nombre):**
```json
{ "name": { "lastNames": "restrepo", "firstNames": "ana" } }
```

**Response `200 OK`:**
```json
{
  "content": [
    { "uuid": "034820b2-…", "document": { "type": "CEDULA_DE_CIUDADANIA", "number": "1098765432" },
      "firstNames": "Ana María", "lastNames": "Restrepo Gómez", "birthDate": "1990-04-12", "sex": "FEMALE", "status": "ACTIVE" }
  ],
  "page": { "size": 20, "number": 0, "totalElements": 1, "totalPages": 1 }
}
```

---

### Actualizaciones — requieren `If-Match`

| Método y ruta                     | Cuerpo                                                     |
| --------------------------------- | ---------------------------------------------------------- |
| `PUT /{uuid}/document`            | `{ "type": "CEDULA_DE_CIUDADANIA", "number": "1098765432" }` (valida edad) |
| `PUT /{uuid}/demographics`        | Mismo objeto `demographics` del registro (corrige errores) |
| `PUT /{uuid}/contact`             | `{ "contact": { … }, "emergencyContact": { … } }`          |
| `PUT /{uuid}/affiliation`         | Mismo objeto `affiliation` del registro (valida aseguradora) |
| `PUT /{uuid}/residence`           | Mismo objeto `residence` del registro                      |

**Response `200 OK`** con el paciente actualizado y el nuevo `ETag`. Solo se modifican pacientes
activos (`422 PATIENT_NOT_ACTIVE`).

---

### Cambios de estado — requieren `If-Match`

| Método y ruta               | Cuerpo                              | Transición              |
| --------------------------- | ----------------------------------- | ----------------------- |
| `POST /{uuid}/deactivation` | `{ "reason": "Registro duplicado" }` | ACTIVE → INACTIVE       |
| `POST /{uuid}/reactivation` | —                                   | INACTIVE → ACTIVE       |
| `POST /{uuid}/death`        | `{ "dateOfDeath": "2026-09-01" }`   | ACTIVE/INACTIVE → DECEASED (definitivo) |

Los pacientes no se eliminan: la base de datos no concede `DELETE` al usuario de la aplicación.

---

### GET `/{uuid}/history`

Historial completo de versiones del paciente (Hibernate Envers).

**Response `200 OK`:**
```json
[
  { "number": 1, "revisedAt": "2026-09-13T18:45:46Z", "revisedBy": "7d1c3f2e-…", "changeType": "CREATED", "state": { "…": "paciente en esa versión" } },
  { "number": 2, "revisedAt": "2026-09-13T18:46:02Z", "revisedBy": "5a9e…", "changeType": "UPDATED", "state": { "…": "paciente en esa versión" } }
]
```

---

### Pacientes sin identificar — `/api/v1/unidentified-patients`

Personas atendidas en urgencias sin documento ni datos (inconscientes, sin acompañante). Tienen un
agregado propio con código de manilla y se identifican después, vinculándolas con un paciente
registrado o registrándolo en el mismo paso. Toda modificación exige `If-Match`.

| Operación                               | Roles                                                   |
| --------------------------------------- | ------------------------------------------------------- |
| Registrar                               | SUPER_ADMIN, ADMIN, RECEPTIONIST, NURSE, DOCTOR          |
| Consultar                               | SUPER_ADMIN, ADMIN, DOCTOR, NURSE, RECEPTIONIST, MEDICAL_RECORDS |
| Identificar y revertir identificación   | SUPER_ADMIN, ADMIN, MEDICAL_RECORDS                     |
| Registrar fallecimiento                 | SUPER_ADMIN, ADMIN, DOCTOR                              |

#### POST `/`

**Request:**
```json
{ "sex": "MALE", "estimatedBirthYear": 1980, "description": "Hombre adulto inconsciente, camisa azul, cicatriz en la frente" }
```

**Response `201 Created`** con `ETag: "0"`:
```json
{
  "uuid": "5b1f0c2e-…",
  "version": 0,
  "code": "NN-2026-000123",
  "sex": "MALE",
  "estimatedBirthYear": 1980,
  "description": "Hombre adulto inconsciente, camisa azul, cicatriz en la frente",
  "status": { "code": "UNIDENTIFIED", "changedAt": "2026-09-13T20:10:00Z" },
  "audit": { "createdAt": "…", "createdBy": "…", "updatedAt": "…", "updatedBy": "…" }
}
```

#### POST `/{uuid}/identification`

Vincula con un paciente existente **o** registra al paciente real y lo vincula en la misma transacción.
`reason` es obligatorio. El paciente destino debe estar activo.

```json
{ "patientUuid": "034820b2-…", "reason": "Un familiar presentó la cédula" }
```
```json
{ "registration": { "document": { … }, "demographics": { … }, "contact": { … }, "affiliation": { … }, "residence": { … } },
  "reason": "Recuperó la conciencia y dio sus datos" }
```

**Response `200 OK`:** `status.code = IDENTIFIED` y `status.identifiedPatientUuid`.

#### POST `/{uuid}/identification-reversal`

Deshace una identificación equivocada: `{ "reason": "La familia confirmó que no es la persona" }`.
Vuelve a `UNIDENTIFIED` y conserva el motivo.

#### POST `/{uuid}/death`

`{ "dateOfDeath": "2026-09-13" }`. Solo para pacientes aún sin identificar; es definitivo.

---

## 3. Clinical History Service — `/api/v1/clinical`

Historia clínica: atenciones, notas firmadas, diagnósticos, listas de antecedentes, signos vitales,
anexos y copias para el paciente. La identidad del paciente pertenece a `patient-service` y llega por
eventos; aquí el paciente siempre se referencia por `patientUuid`.

**Convenciones del servicio:**

- Una nota nace como **borrador** (`clinical_workspace`, editable y borrable) y al firmarse pasa al
  **libro** (`clinical_ledger`, inmutable). Corregir una nota firmada es escribir una adenda; dejarla
  sin efecto es anularla con motivo.
- Los borradores usan `If-Match` con la versión del `ETag` (`428` sin cabecera, `412` desactualizado).
- Firmar y anular notas, el acceso de emergencia y emitir la copia exigen un segundo factor verificado
  hace 5 minutos o menos: si no, `401 STEP_UP_REQUIRED` con `WWW-Authenticate: Bearer
  error="insufficient_user_authentication", max_age=300`.
- Ver o escribir exige **relación de cuidado**: pertenecer al equipo de esa atención (vigente mientras
  esté abierta y 30 días después de cerrada). Sin ella, `403` — y el rechazo queda auditado.
- Los errores siguen RFC 9457 con `code` estable y `traceId`.

**Roles:**

| Operación                                              | Roles                                        |
| ------------------------------------------------------ | -------------------------------------------- |
| Atenciones, notas, anexos, listas y signos vitales      | DOCTOR, NURSE (+ relación de cuidado)        |
| Verificar integridad y firma                            | DOCTOR, NURSE, MEDICAL_RECORDS, ADMIN, SUPER_ADMIN |
| Copia de la historia para el paciente                   | MEDICAL_RECORDS                              |
| Catálogo CIE-10: importar y activar                     | SUPER_ADMIN                                  |
| Rotación de claves de cifrado                           | SUPER_ADMIN                                  |

### POST `/encounters`

Abre una atención. Quien la abre entra en el equipo de cuidado.

```json
{ "patientUuid": "3f1c…", "type": "EMERGENCY", "admissionId": "A-2026-114" }
```

`type`: `OUTPATIENT`, `EMERGENCY`, `INPATIENT` o `TELEHEALTH`. `admissionId` es opcional y solo referencia la
admisión administrativa. Responde `201` con el id de la atención. Si el paciente no está en la copia
local y `patient-service` no responde, devuelve `503` sin afectar al resto del servicio.

### GET `/encounters/{id}`

Devuelve la atención con su equipo de cuidado y sus notas firmadas. Las notas restringidas solo
aparecen para su autor y el equipo de esa atención.

### POST `/encounters/{id}/closure`

Cierra la atención. Solo el equipo de cuidado de esa atención; queda sellado en la cadena.

### GET `/patients/{patientUuid}/encounters`

Atenciones del paciente, de la más reciente a la más antigua.

### POST `/encounters/{id}/care-team`

Suma a alguien al equipo de cuidado, solo con la atención abierta.

```json
{ "clinicianUuid": "9a2e…", "role": "DOCTOR" }
```

### POST `/patients/{patientUuid}/emergency-access`

Romper el vidrio: acceso de urgencia sin relación previa.

```json
{ "reason": "Paciente inconsciente en reanimación, requiere antecedentes" }
```

El motivo exige 10 caracteres o más, dura 4 horas, se guarda cifrado, cubre también las notas
restringidas y se publica en la auditoría.

### POST `/encounters/{id}/drafts`

Crea el borrador de una nota. `content` es el contenido estructurado según su `type`
(`ADMISSION`, `PROGRESS`, `CONSULTATION` y `DISCHARGE` las escribe un médico; `NURSING` enfermería;
`TRIAGE` y `ADDENDUM` cualquiera de los dos), `updates` son los cambios
en listas vivas y signos vitales que se aplicarán al firmar, `restriction` marca la categoría
sensible y `occurredAt` permite registrar una nota extemporánea con su fecha real de atención.

```json
{
  "content": {
    "type": "PROGRESS",
    "subjective": "Refiere mejoría del dolor",
    "objective": "Alerta, hidratada, abdomen blando",
    "assessment": "Evolución favorable",
    "plan": "Continuar manejo",
    "diagnoses": [{ "code": "I10X", "role": "PRINCIPAL", "type": "CONFIRMED_REPEATED" }]
  },
  "updates": [
    { "kind": "RECORD_VITAL_SIGNS", "measuredAt": "2026-09-14T13:05:00Z",
      "readings": [{ "kind": "HEART_RATE", "value": 78 }] }
  ],
  "restriction": null,
  "occurredAt": null
}
```

`role` del diagnóstico: `PRINCIPAL` o `RELATED`; `type`: `IMPRESSION`, `CONFIRMED_NEW` o
`CONFIRMED_REPEATED`. La nota de ingreso y la epicrisis exigen diagnóstico principal (`422` si falta).
`restriction`: `MENTAL_HEALTH`, `SEXUAL_HEALTH`, `HIV` o `VIOLENCE`.

La adenda es una nota más: `{ "type": "ADDENDUM", "amendsNoteId": "…", "text": "…" }`. Es la única
que se puede escribir con la atención ya cerrada, porque corregir una nota firmada nunca debe exigir
reabrirla.

### GET `/drafts` · GET `/drafts/{id}` · PUT `/drafts/{id}` · DELETE `/drafts/{id}`

Borradores propios. Un borrador ajeno responde `404`, no `403`: quien no es su autor no debe saber que
existe. `PUT` y `DELETE` exigen `If-Match`.

### POST `/drafts/{id}/signature`

Firma el borrador y lo convierte en nota inmutable: calcula el SHA-256 del contenido canónico, lo
sella con la clave institucional, lo encadena, archiva los anexos en almacenamiento WORM y aplica los
`updates` a las listas vivas y los signos vitales. Exige `If-Match`. Responde `201` con la nota, su
firma y sus anexos.

### GET `/notes/{id}` · POST `/notes/{id}/void`

Consulta una nota firmada o la anula con motivo (`{ "reason": "…" }`). Anular no borra: agrega un
registro encadenado, revierte el efecto de la nota sobre las listas vivas y deja el original legible
para quien ya podía verlo. Solo el autor y solo con la atención abierta.

### Anexos

| Operación | Endpoint                                        |
| --------- | ----------------------------------------------- |
| Adjuntar  | `POST /drafts/{draftId}/attachments` (multipart `file`) |
| Listar    | `GET /drafts/{draftId}/attachments`             |
| Quitar    | `DELETE /drafts/{draftId}/attachments/{attachmentId}` |
| Descargar | `GET /notes/{noteId}/attachments/{attachmentId}` |

Solo PDF, JPEG y PNG, hasta 20 MB, validados por sus bytes iniciales y no por la extensión. El archivo
se cifra antes de subirse y solo se archiva con retención WORM cuando se firma la nota; los que nunca
se firman se purgan. La descarga verifica el SHA-256 antes de responder y queda auditada.

### GET `/patients/{patientUuid}/lists`

Listas vivas del paciente, con `category` opcional: `ALLERGY`, `CHRONIC_CONDITION`,
`CURRENT_MEDICATION`, `FAMILY_HISTORY`, `PAST_HISTORY`, `VACCINATION`. Cada ítem trae la nota que lo
originó; nada se borra, cambia de estado.

### GET `/patients/{patientUuid}/vital-signs`

Serie de signos vitales, con `kind` y rango de fechas opcionales.

### GET `/patients/{patientUuid}/integrity`

Recalcula la cadena de hashes del paciente y responde `verified` con el detalle por registro. No
expone contenido clínico, por eso también lo pueden consultar ADMIN y MEDICAL_RECORDS. Un registro
cuya clave de cifrado no se puede abrir aparece como `UNREADABLE_ENTRY`.

### GET `/notes/{id}/signature` · GET `/seal-keys`

Firma de una nota (hash, sello, `keyId`, autor y fecha) y claves públicas del sello. `/seal-keys` es
público: sirve para verificar una historia sin credenciales.

### POST `/patients/{patientUuid}/record-copies`

Genera la copia de la historia para el paciente en PDF (rol `MEDICAL_RECORDS`).

```json
{ "reason": "Solicitud de la paciente en ventanilla" }
```

Incluye notas restringidas y anuladas, los hashes por registro y una página de verificación; el
SHA-256 del PDF se sella con la clave institucional. `GET /record-copies/{copyId}` devuelve sus
metadatos y `POST /record-copies/{copyId}/verification` (multipart `file`) comprueba que un PDF
recibido es exactamente el emitido. Cada copia se audita con su motivo.

### Catálogo CIE-10

| Operación                 | Endpoint                                                   | Rol           |
| ------------------------- | ---------------------------------------------------------- | ------------- |
| Buscar códigos            | `GET /terminology/cie10?q=R07&limit=20`                    | DOCTOR, NURSE |
| Listar versiones          | `GET /admin/terminology/cie10/releases`                    | SUPER_ADMIN   |
| Importar versión          | `POST /admin/terminology/cie10/releases` (multipart `file`) | SUPER_ADMIN   |
| Activar versión           | `POST /admin/terminology/cie10/releases/{id}/activation`   | SUPER_ADMIN   |

La importación es idempotente por checksum del archivo y devuelve las advertencias encontradas. Sin
catálogo activo, una nota con diagnósticos responde `503 TERMINOLOGY_NOT_ACTIVE`.

### Administración de claves

`GET /admin/encryption` lista las claves maestras y `POST /admin/encryption/rewrap` vuelve a envolver
las DEK con la clave activa. Solo `SUPER_ADMIN`; el procedimiento está en
`clinical-history-service/docs/claves-y-cifrado.md`.

---

## 4. Admissions Service — `/api/v1/attentions`

### GET `/`

Lista atenciones con filtros opcionales.

**Query params:**

| Param      | Tipo   | Descripción                              |
| ---------- | ------ | ---------------------------------------- |
| `status`   | string | `CREATED`, `IN_PROGRESS`, `DISCHARGED`, `CANCELLED` |
| `triage`   | string | `RED`, `ORANGE`, `YELLOW`, `GREEN`, `BLUE` |
| `date`     | string | Fecha en formato `YYYY-MM-DD`            |
| `doctorId` | UUID   | Filtrar por médico asignado              |

**Response `200 OK`:**
```json
{
  "content": [
    {
      "id": "att001-...",
      "patientId": "p1a2b3c4-...",
      "patientName": "Carlos Rodríguez",
      "triageLevel": "YELLOW",
      "status": "IN_PROGRESS",
      "assignedDoctorId": "doc001-...",
      "createdAt": "2025-05-19T08:30:00"
    }
  ],
  "totalElements": 18
}
```

---

### POST `/`

Crea una nueva atención médica.

**Request:**
```json
{
  "patientId": "p1a2b3c4-...",
  "triageLevel": "ORANGE",
  "reason": "Dolor torácico agudo con irradiación al brazo izquierdo",
  "assignedDoctorId": "doc001-...",
  "authorizationCode": "EPS-2025-001234"
}
```

**Response `201 Created`:**
```json
{
  "id": "att999-...",
  "status": "CREATED",
  "triageLevel": "ORANGE",
  "createdAt": "2025-05-19T14:22:00"
}
```

---

### GET `/{id}`

Retorna los detalles completos de una atención.

**Response `200 OK`:**
```json
{
  "id": "att001-...",
  "patientId": "p1a2b3c4-...",
  "triageLevel": "YELLOW",
  "status": "IN_PROGRESS",
  "reason": "Fiebre alta y tos seca",
  "assignedDoctorId": "doc001-...",
  "movements": [
    {
      "id": "mov001",
      "fromStatus": "CREATED",
      "toStatus": "IN_PROGRESS",
      "performedBy": "dr.martinez",
      "timestamp": "2025-05-19T09:05:00"
    }
  ],
  "authorization": {
    "code": "EPS-2025-001234",
    "status": "APPROVED",
    "provider": "EPS Sura"
  }
}
```

---

### PATCH `/{id}/status`

Cambia el estado de una atención.

**Request:**
```json
{
  "newStatus": "DISCHARGED",
  "notes": "Paciente estabilizado, alta con prescripción."
}
```

**Response `200 OK`**

**Transiciones válidas:**

| Estado actual | Estados destino válidos         |
| ------------- | ------------------------------- |
| `CREATED`     | `IN_PROGRESS`, `CANCELLED`      |
| `IN_PROGRESS` | `DISCHARGED`, `CANCELLED`       |
| `DISCHARGED`  | (estado final)                  |
| `CANCELLED`   | (estado final)                  |

---

## 5. Suppliers Service — `/api/v1/doctors`

### GET `/`

Lista médicos activos con su especialidad.

**Response `200 OK`:**
```json
{
  "content": [
    {
      "id": "doc001-...",
      "firstName": "Ana",
      "lastName": "Martínez",
      "specialty": "Cardiología",
      "licenseNumber": "RM-12345",
      "email": "a.martinez@clinica.com",
      "status": "ACTIVE"
    }
  ]
}
```

---

### GET `/{id}/schedule`

Retorna el horario semanal del médico.

**Response `200 OK`:**
```json
{
  "doctorId": "doc001-...",
  "schedule": [
    {
      "dayOfWeek": "MONDAY",
      "startTime": "08:00",
      "endTime": "14:00"
    },
    {
      "dayOfWeek": "WEDNESDAY",
      "startTime": "14:00",
      "endTime": "20:00"
    }
  ]
}
```

---

### POST `/{id}/unavailability`

Registra un período de no disponibilidad (vacaciones, incapacidad, etc.).

**Request:**
```json
{
  "reason": "Vacaciones",
  "startDate": "2025-07-01",
  "endDate": "2025-07-15"
}
```

**Response `201 Created`**

---

## 6. Clients Service — `/api/v1/health-providers`

### GET `/`

Lista todos los proveedores de salud (aseguradoras, EPS).

**Response `200 OK`:**
```json
{
  "content": [
    {
      "id": "hp001-...",
      "name": "EPS Sura",
      "nit": "890903790-1",
      "type": "EPS",
      "contactEmail": "contratos@sura.com.co",
      "status": "ACTIVE"
    }
  ]
}
```

---

### GET `/{id}/contracts`

Retorna los contratos activos con un proveedor.

**Response `200 OK`:**
```json
{
  "providerId": "hp001-...",
  "contracts": [
    {
      "id": "con001-...",
      "number": "CLN-2025-001",
      "startDate": "2025-01-01",
      "endDate": "2025-12-31",
      "coverageType": "FULL",
      "status": "ACTIVE"
    }
  ]
}
```

---

## 7. AI Assistant Service — `/api/v1/ai`

### POST `/chat`

Envía un mensaje al asistente y recibe respuesta con posibles acciones ejecutadas.

**Request:**
```json
{
  "sessionId": "session-abc123",
  "message": "El paciente Carlos Rodríguez, CC 1234567890, llega con dolor torácico severo, necesito registrar atención urgente nivel ROJO"
}
```

**Response `200 OK`:**
```json
{
  "sessionId": "session-abc123",
  "reply": "He registrado la atención de urgencia para Carlos Rodríguez (CC 1234567890) con triage RED. ID de atención: att999-...",
  "actionsPerformed": [
    {
      "type": "CREATE_ATTENTION",
      "attentionId": "att999-...",
      "triageLevel": "RED",
      "patientId": "p1a2b3c4-..."
    }
  ],
  "timestamp": "2025-05-19T14:22:05"
}
```

---

### GET `/conversations/{sessionId}`

Retorna el historial de una sesión de conversación.

**Response `200 OK`:**
```json
{
  "sessionId": "session-abc123",
  "messages": [
    {
      "role": "USER",
      "content": "El paciente Carlos Rodríguez...",
      "timestamp": "2025-05-19T14:22:00"
    },
    {
      "role": "ASSISTANT",
      "content": "He registrado la atención de urgencia...",
      "timestamp": "2025-05-19T14:22:05"
    }
  ]
}
```

---

## 8. Códigos de Error Comunes

| Código | Significado                                          |
| ------ | ---------------------------------------------------- |
| `400`  | Solicitud malformada o datos de validación inválidos |
| `401`  | Token ausente, expirado o firma inválida             |
| `403`  | El rol del usuario no tiene permiso para este recurso |
| `404`  | Recurso no encontrado                                |
| `409`  | Conflicto de estado (ej. transición de estado inválida) |
| `422`  | Entidad no procesable (regla de negocio violada)     |
| `429`  | Rate limit excedido — demasiadas solicitudes         |
| `500`  | Error interno del servidor                           |
| `503`  | Servicio no disponible (circuit breaker abierto)     |

**Formato de error estándar:**
```json
{
  "timestamp": "2025-05-19T14:30:00",
  "status": 422,
  "error": "Unprocessable Entity",
  "message": "No se puede dar de alta una atención CANCELLED.",
  "path": "/api/v1/attentions/att001-..."
}
```

---

_Documentación mantenida junto al código — los ejemplos reflejan el comportamiento actual de la API._
