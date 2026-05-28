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

### GET `/`

Lista todos los pacientes activos con paginación.

**Query params:**

| Param  | Tipo    | Default | Descripción           |
| ------ | ------- | ------- | --------------------- |
| `page` | integer | `0`     | Número de página      |
| `size` | integer | `20`    | Elementos por página  |
| `sort` | string  | `lastName,asc` | Campo y dirección |

**Response `200 OK`:**
```json
{
  "content": [
    {
      "id": "p1a2b3c4-...",
      "identificationNumber": "1234567890",
      "identificationType": "CC",
      "firstName": "Carlos",
      "lastName": "Rodríguez",
      "dateOfBirth": "1985-03-15",
      "gender": "MALE",
      "phone": "+57 310 555 0101",
      "email": "c.rodriguez@email.com",
      "insuranceAffiliation": "EPS Sura",
      "createdAt": "2025-01-10T09:00:00"
    }
  ],
  "totalElements": 142,
  "totalPages": 8,
  "size": 20,
  "number": 0
}
```

---

### POST `/`

Registra un nuevo paciente.

**Request:**
```json
{
  "identificationNumber": "9876543210",
  "identificationType": "CC",
  "firstName": "María",
  "lastName": "González",
  "dateOfBirth": "1990-07-22",
  "gender": "FEMALE",
  "phone": "+57 311 555 0202",
  "email": "m.gonzalez@email.com",
  "address": "Calle 45 # 12-34, Bogotá",
  "insuranceAffiliation": "EPS Sanitas"
}
```

**Response `201 Created`:**
```json
{
  "id": "p9z8y7x6-...",
  "identificationNumber": "9876543210",
  "firstName": "María",
  "lastName": "González",
  "createdAt": "2025-05-19T10:15:00"
}
```

---

### GET `/{id}`

Retorna el perfil completo de un paciente con toda su historia clínica.

**Response `200 OK`:**
```json
{
  "id": "p1a2b3c4-...",
  "firstName": "Carlos",
  "lastName": "Rodríguez",
  "dateOfBirth": "1985-03-15",
  "gender": "MALE",
  "medicalHistory": {
    "bloodType": "O+",
    "notes": "Paciente con historial de alergias estacionales."
  },
  "allergies": [
    {
      "id": "al001",
      "substance": "Penicilina",
      "severity": "HIGH",
      "reaction": "Anafilaxia"
    }
  ],
  "chronicDiseases": [
    {
      "id": "cd001",
      "name": "Hipertensión arterial",
      "diagnosedAt": "2018-06-01"
    }
  ],
  "currentMedications": [
    {
      "id": "med001",
      "name": "Losartán",
      "dosage": "50mg",
      "frequency": "Once daily"
    }
  ],
  "vaccinationRecords": [
    {
      "id": "vac001",
      "vaccine": "COVID-19 — Pfizer",
      "appliedAt": "2021-04-15",
      "nextDoseAt": "2021-05-06"
    }
  ]
}
```

---

### PUT `/{id}`

Actualiza los datos demográficos de un paciente.

**Request:** (campos a modificar)
```json
{
  "phone": "+57 310 555 9999",
  "address": "Carrera 7 # 80-15, Bogotá"
}
```

**Response `200 OK`:** paciente actualizado

---

### DELETE `/{id}`

Soft delete del paciente (marca `deletedAt`).

**Response `204 No Content`**

---

### POST `/{id}/allergies`

Agrega una alergia al historial del paciente.

**Request:**
```json
{
  "substance": "Ibuprofeno",
  "severity": "MEDIUM",
  "reaction": "Urticaria"
}
```

**Response `201 Created`**

---

### POST `/{id}/vaccinations`

Registra una vacuna aplicada.

**Request:**
```json
{
  "vaccine": "Influenza estacional",
  "appliedAt": "2025-03-10",
  "nextDoseAt": "2026-03-10",
  "appliedBy": "Dr. Pérez"
}
```

**Response `201 Created`**

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
