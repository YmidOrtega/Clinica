# Referencia de API REST — Clínica

**Versión:** 1.0  
**Base URL:** `http://localhost:8080` (API Gateway)  
**Autenticación:** Bearer JWT (RSA-256) en header `Authorization`

---

## Convenciones

- Todos los endpoints requieren `Authorization: Bearer <token>` salvo los de autenticación.
- Las respuestas exitosas devuelven `2xx`; los errores siguen el formato estándar de Spring.
- Los UUIDs se expresan como strings en formato `xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx`.
- Los timestamps usan ISO 8601: `2025-05-19T14:30:00`.
- Soft delete: los registros eliminados tienen `deletedAt` no nulo y no aparecen en listados normales.

---

## 1. Auth Service — `/api/v1/auth`

### POST `/login`

Autentica un usuario y devuelve access token + refresh token.

**Request:**
```json
{
  "username": "dr.martinez",
  "password": "SecurePass123!"
}
```

**Response `200 OK`:**
```json
{
  "accessToken": "eyJhbGciOiJSUzI1NiJ9...",
  "refreshToken": "d4f8a2b1-...",
  "tokenType": "Bearer",
  "expiresIn": 900,
  "user": {
    "id": "a1b2c3d4-...",
    "username": "dr.martinez",
    "email": "martinez@clinica.com",
    "roles": ["ROLE_DOCTOR"]
  }
}
```

**Errores:**

| Código | Causa                                      |
| ------ | ------------------------------------------ |
| `401`  | Credenciales inválidas                     |
| `423`  | Cuenta bloqueada por intentos fallidos     |

---

### POST `/refresh`

Renueva el access token usando el refresh token.

**Request:**
```json
{
  "refreshToken": "d4f8a2b1-..."
}
```

**Response `200 OK`:**
```json
{
  "accessToken": "eyJhbGciOiJSUzI1NiJ9...",
  "expiresIn": 900
}
```

---

### POST `/logout`

Invalida el refresh token actual.

**Request:**
```json
{
  "refreshToken": "d4f8a2b1-..."
}
```

**Response `204 No Content`**

---

### POST `/password-reset/request`

Inicia el flujo de restablecimiento de contraseña.

**Request:**
```json
{
  "email": "martinez@clinica.com"
}
```

**Response `200 OK`:**
```json
{
  "message": "Si el correo existe, recibirá instrucciones de restablecimiento."
}
```

---

### POST `/password-reset/confirm`

Establece la nueva contraseña usando el token recibido.

**Request:**
```json
{
  "token": "abc123-reset-token",
  "newPassword": "NuevaContraseña456!"
}
```

**Response `200 OK`:**
```json
{
  "message": "Contraseña actualizada correctamente."
}
```

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

## 3. Admissions Service — `/api/v1/attentions`

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

## 4. Suppliers Service — `/api/v1/doctors`

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

## 5. Clients Service — `/api/v1/health-providers`

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

## 6. AI Assistant Service — `/api/v1/ai`

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

## 7. Códigos de Error Comunes

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
