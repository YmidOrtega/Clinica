# Arquitectura Técnica — Clínica

**Versión:** 1.0  
**Stack:** Java 21 · Spring Boot 3.5 · Spring Cloud 2025.0  
**Dominio:** Gestión hospitalaria — pacientes, admisiones, proveedores, facturación, autenticación, IA

---

## 1. Contexto del Problema

Las clínicas medianas operan con múltiples dominios que cambian a ritmos distintos: el registro de pacientes no tiene el mismo ciclo de actualización que la facturación o la gestión de turnos médicos. Unificar todo en un monolito crea acoplamiento innecesario y bloquea el despliegue independiente de cambios críticos.

Adicionalmente, un sistema de salud tiene requisitos no negociables:

- **Auditoría completa** — cada cambio en un expediente clínico debe quedar registrado con usuario, fecha y acción.
- **Eliminación segura** — los datos médicos no se borran físicamente; se marcan como inactivos con trazabilidad.
- **Seguridad robusta** — credenciales en tránsito, tokens de corta vida, bloqueo por intentos fallidos, RBAC por rol profesional.
- **Disponibilidad ante fallos parciales** — si el servicio de facturación cae, las admisiones deben seguir funcionando.

---

## 2. Visión General del Sistema

```
                         ┌─────────────────────────────────┐
                         │        Clientes Externos        │
                         │   Browser · Apps · Integraciones│
                         └─────────────┬───────────────────┘
                                       │ HTTPS / JWT
                                       ▼
                    ┌──────────────────────────────────────┐
                    │           API Gateway                │
                    │   Spring Cloud Gateway 4.3           │
                    │  ┌──────────┬──────────┬──────────┐  │
                    │  │ Logging  │Rate Limit│ Retry    │  │
                    │  │ Routing  │  Redis   │Circuit   │  │
                    │  └──────────┴──────────┴──────────┘  │
                    └────┬──────┬──────┬──────┬────┬───────┘
                         │      │      │      │    │
              ┌──────────┘  ┌───┘  ┌───┘  ┌──┘    └──────┐
              ▼             ▼      ▼      ▼               ▼
        ┌──────────┐ ┌──────────┐ ┌────────┐ ┌──────────┐ ┌─────────┐
        │ Patient  │ │Admissions│ │ Auth   │ │Suppliers │ │ Clients │
        │ Service  │ │ Service  │ │Service │ │ Service  │ │ Service │
        │  MySQL   │ │PostgreSQL│ │ MySQL  │ │  MySQL   │ │  MySQL  │
        └──────────┘ └──────────┘ └────────┘ └──────────┘ └─────────┘
              │                        │
              ▼                        ▼
        ┌──────────┐           ┌───────────────┐
        │AI Assist │           │ Eureka Server │
        │PostgreSQL│           │   Discovery   │
        └──────────┘           └───────────────┘
                                       ▲
                        Todos los servicios se registran aquí
```

---

## 3. Stack Tecnológico

| Capa              | Tecnología                          | Justificación                                                                    |
| ----------------- | ----------------------------------- | -------------------------------------------------------------------------------- |
| Runtime           | Java 21                             | Soporte LTS, records, sealed classes, pattern matching                           |
| Framework         | Spring Boot 3.5 + Spring Cloud 2025 | Ecosistema maduro para microservicios; auto-configuración, actuator, seguridad   |
| API Gateway       | Spring Cloud Gateway 4.3            | Reactivo (WebFlux); rate limiting, logging y circuit breaker sin código custom   |
| Service Discovery | Netflix Eureka                      | Registro dinámico; los servicios se localizan por nombre, no por IP              |
| Seguridad         | Spring Security 6 + JWT RSA-256     | Tokens asimétricos: Auth emite con clave privada, cada servicio valida con pública|
| Bases de datos    | MySQL 8 · PostgreSQL 16             | MySQL para dominios relacionales simples; PostgreSQL para datos transaccionales  |
| Migraciones       | Flyway                              | Historial versionado de esquema; obligatorio en sistemas de salud (auditoría)    |
| ORM               | Hibernate + MapStruct 1.6           | JPA estándar; MapStruct genera el código de mapping en compile time (zero reflect)|
| Resiliencia       | Resilience4j                        | Circuit breaker, retry con backoff exponencial, fallback declarativo             |
| Cache / Rate Limit| Redis 7                             | Cache distribuida + token bucket para rate limiting en el gateway               |
| IA                | Spring AI + Gemini API              | Abstracción portable (Gemini en prod, LM Studio en local)                        |
| Contenedores      | Docker + Compose                    | Stack completo levantable con un solo comando                                    |
| Testing           | JUnit 5 + Mockito                   | Pruebas unitarias de servicios, controladores e integraciones                    |
| Build             | Maven multi-módulo                  | Un POM padre gestiona versiones de dependencias para todos los servicios         |

---

## 4. Microservicios — Responsabilidades

### 4.1 Patient Service (`:8082`)

Propietario del dominio clínico del paciente.

```
PatientController
    └── PatientService
          ├── PatientRepository          → Tabla patients (MySQL)
          ├── AllergyRepository          → Tabla allergies
          ├── ChronicDiseaseRepository   → Tabla chronic_diseases
          ├── MedicationRepository       → Tabla current_medications
          ├── FamilyHistoryRepository    → Tabla family_histories
          └── VaccinationRepository      → Tabla vaccination_records
```

**Entidades principales:**

| Entidad           | Descripción                                                    |
| ----------------- | -------------------------------------------------------------- |
| `Patient`         | Datos demográficos: UUID, tipo/número de identificación, DOB, género, contacto, afiliación |
| `MedicalHistory`  | Historia clínica general del paciente                          |
| `Allergy`         | Alergias con nivel de severidad                                |
| `ChronicDisease`  | Enfermedades crónicas diagnosticadas                           |
| `CurrentMedication` | Medicamentos activos con dosis y frecuencia                  |
| `FamilyHistory`   | Antecedentes familiares relevantes                             |
| `VaccinationRecord` | Registro de vacunas aplicadas                                |

**Soft delete:** `deleted_at` + `deleted_by` en todas las tablas — los registros nunca se eliminan físicamente.

### 4.2 Admissions Service (`:8083`)

Gestiona el ciclo de vida completo de una atención médica.

```
AttentionController
    └── AttentionService
          ├── AttentionRepository        → Tabla attentions (PostgreSQL)
          ├── AttentionMovementRepository
          ├── AttentionUserHistoryRepository
          └── AuthorizationRepository
```

**Estado de una atención:**

```
[Paciente llega]
      │
      ▼
  CREATED ──────────────────► CANCELLED
      │
      ▼
 IN_PROGRESS
      │
      ├── (facturación validada)
      │
      ▼
 DISCHARGED
```

**Niveles de triage:**

| Color    | Prioridad | Descripción                           |
| -------- | --------- | ------------------------------------- |
| `RED`    | 1         | Emergencia crítica — atención inmediata |
| `ORANGE` | 2         | Emergencia urgente                    |
| `YELLOW` | 3         | Urgencia moderada                     |
| `GREEN`  | 4         | No urgente                            |
| `BLUE`   | 5         | Consulta rutinaria                    |

### 4.3 Auth Service (`:8084`)

Autenticación centralizada. Emite tokens JWT firmados con RSA-256.

```
AuthController
    ├── AuthenticationService  → Login, logout, refresh token
    ├── UserService            → Registro, cambio de contraseña, bloqueo
    ├── PasswordResetService   → Flujo de reset por token
    └── AuditService           → Registro de todos los eventos de acceso
```

### 4.4 Suppliers Service (`:8085`)

Gestión del personal médico y sus disponibilidades.

```
DoctorController
    └── DoctorService
          ├── DoctorRepository       → Tabla doctors
          ├── ScheduleRepository     → Tabla doctor_schedules
          └── UnavailabilityRepository → Tabla doctor_unavailability
```

### 4.5 Clients Service (`:8086`)

Proveedores de salud: aseguradoras, EPS, redes de clínicas.

```
HealthProviderController
    └── HealthProviderService
          ├── HealthProviderRepository → Tabla health_providers
          ├── ContractRepository       → Tabla contracts
          └── PortfolioRepository      → Tabla portfolios
```

### 4.6 AI Assistant Service (`:8087`)

Asistente conversacional con memoria de sesión e integración con el flujo de admisiones.

```
AIAssistantController
    └── AIAssistantService
          ├── GeminiClient (Spring AI)  → Llamadas a la API de Gemini
          ├── ConversationRepository    → Tabla conversation_history
          └── MessageRepository         → Tabla conversation_messages
```

El asistente detecta intención en la conversación: si el médico describe síntomas de un paciente, puede iniciar automáticamente la creación de una atención llamando al Admissions Service internamente.

### 4.7 API Gateway (`:8080`)

Punto de entrada único para todo el tráfico externo.

```
Filtros de entrada:
  LoggingFilter   → Registra cada request (método, path, status, tiempo) en PostgreSQL
  RateLimitFilter → Token bucket por usuario en Redis: max N req/seg
  AuthFilter      → Valida JWT (clave pública RSA-256) antes de enrutar

Por cada ruta configurada:
  Resilience4j:
    CircuitBreaker → Abre después de X fallos consecutivos
    Retry          → Backoff exponencial, máx 3 intentos
    TimeLimiter    → Timeout por request
```

---

## 5. Service Discovery — Eureka

```
Arranque de un microservicio:
  1. Spring Cloud Eureka Client se activa
  2. Servicio publica: { serviceId, host, port, status: UP }
  3. Eureka mantiene heartbeat cada 30s; si falla → marca DOWN
  4. API Gateway consulta el registro para resolver rutas

Sin Eureka: cada servicio necesitaría IPs hardcodeadas
Con Eureka: el gateway resuelve "PATIENT-SERVICE" → IP actual dinámicamente
```

**Beneficio en Docker Compose:** al reiniciar un contenedor con nueva IP, Eureka lo re-registra automáticamente. Ninguna configuración cambia.

---

## 6. Estrategia de Base de Datos

Cada servicio es dueño exclusivo de su base de datos. No hay JOINs entre servicios.

| Servicio         | Motor      | Justificación                                                          |
| ---------------- | ---------- | ---------------------------------------------------------------------- |
| Patient          | MySQL 8    | Esquema relacional estable, buena integración con Hibernate             |
| Admissions       | PostgreSQL | Enums nativos para triage y estados; mejor soporte para audit triggers |
| Auth             | MySQL 8    | Tablas de usuarios con índices en email y username                     |
| Suppliers        | MySQL 8    | Relaciones médico ↔ especialidad                                       |
| Clients          | MySQL 8    | Datos de proveedores y contratos                                       |
| AI Assistant     | PostgreSQL | JSONB para almacenar mensajes con metadata flexible                    |
| API Gateway      | PostgreSQL | Logs de analytics: volumen alto de escritura, queries de agregación    |

**Flyway:** cada servicio tiene su carpeta `db/migration/` con archivos `V{n}__{descripcion}.sql`. Las migraciones corren automáticamente al arrancar el servicio.

---

## 7. Patrones de Resiliencia

### 7.1 Circuit Breaker

```
Estado CLOSED (normal):
  Requests pasan al servicio destino

Estado OPEN (servicio caído):
  Se activa cuando la tasa de fallos > umbral configurado
  Las requests fallan rápido → no esperan timeout
  Se llama el método @Fallback

Estado HALF-OPEN (recuperación):
  Permite algunas requests de prueba
  Si tienen éxito → vuelve a CLOSED
  Si fallan → vuelve a OPEN
```

### 7.2 Retry con Backoff Exponencial

```
Intento 1 → falla
Espera 500ms
Intento 2 → falla
Espera 1000ms
Intento 3 → falla
→ Fallback o error al cliente
```

### 7.3 Rate Limiting (Redis)

```
Por cada request que llega al gateway:
  1. Obtiene userId del JWT
  2. Consulta en Redis: tokens_disponibles[userId]
  3. Si tokens > 0 → decrementa y permite
  4. Si tokens = 0 → 429 Too Many Requests
  5. Redis recarga tokens a tasa configurable (token bucket)
```

---

## 8. Asistente IA — Integración

```
Cliente              AI Service            Admissions Service
   │                     │                        │
   │ POST /chat          │                        │
   │ {"message":"..."}   │                        │
   │─────────────────►   │                        │
   │                     │ Gemini API             │
   │                     │ (Spring AI)            │
   │                     │ ─────────────►         │
   │                     │ ◄─────────────         │
   │                     │                        │
   │                     │ (detecta intención)    │
   │                     │ POST /api/v1/attentions│
   │                     │ ───────────────────►   │
   │                     │ ◄───────────────────   │
   │                     │                        │
   │ ◄─────────────────  │                        │
   │ respuesta + acción  │                        │
```

Spring AI actúa como capa de abstracción: cambiando la configuración se puede apuntar a Gemini (producción) o a LM Studio (desarrollo local sin costos de API).

---

## 9. Decisiones de Diseño Clave

### 9.1 JWT con RSA-256 en lugar de HMAC-SHA

**Decisión:** Auth Service firma tokens con una clave privada RSA. Cada microservicio solo tiene la clave pública para verificar.  
**Por qué:** en arquitectura de microservicios con HMAC, todos los servicios necesitarían la misma clave secreta — un secreto compartido entre N servicios es una superficie de ataque N veces mayor. Con RSA asimétrico, comprometer un microservicio no expone la capacidad de emitir tokens.

### 9.2 Flyway sobre scripts manuales

**Decisión:** todas las migraciones de esquema son archivos Flyway versionados.  
**Por qué:** en un sistema de salud, los cambios de esquema son auditables por regulación. Flyway garantiza que la versión del esquema en producción es exactamente reproducible y no puede aplicarse en orden incorrecto.

### 9.3 Soft Deletes en todos los dominios

**Decisión:** ninguna entidad del sistema se elimina con `DELETE` SQL.  
**Por qué:** los expedientes médicos tienen valor legal. La capacidad de restaurar un registro "eliminado" es un requisito de cumplimiento. El campo `deleted_at` permite filtrar lógicamente sin perder el dato.

### 9.4 MapStruct sobre ModelMapper o conversión manual

**Decisión:** MapStruct genera el código de mapping entre entidades y DTOs en tiempo de compilación.  
**Por qué:** ModelMapper usa reflection en runtime (lento, difícil de debuggear). Mapping manual es verbose y propenso a errores al agregar campos. MapStruct: sin reflection, error de compilación si falta un campo mapeado, tan rápido como código manual.

### 9.5 Un base de datos por servicio

**Decisión:** no hay tablas compartidas entre microservicios. Las referencias cruzadas se hacen por ID.  
**Por qué:** el acoplamiento a nivel de esquema es la forma más peligrosa de acoplamiento en microservicios — un cambio de columna en la tabla de pacientes rompe todos los servicios que hacen JOIN directamente. Con bases de datos independientes, cada servicio evoluciona su esquema de forma autónoma.

---

## 10. Estructura del Proyecto

```
Clinica/
├── BackEnd-Clinica/
│   ├── pom.xml                         # POM padre — gestión de dependencias
│   ├── docker-compose.yml              # Stack completo
│   ├── .env                            # Variables de entorno
│   ├── eureka-service/                 # Registro de servicios
│   ├── api-gateway/                    # Gateway + rate limiting + analytics
│   ├── auth-service/                   # JWT, usuarios, roles
│   │   └── src/main/java/.../
│   │       ├── module/entity/          # User, Role, RefreshToken, AuditLog
│   │       ├── module/service/         # Auth, User, PasswordReset
│   │       └── module/controller/      # AuthController
│   ├── patient-service/                # Pacientes y expedientes
│   ├── admissions-service/             # Atenciones, triage, autorizaciones
│   ├── suppliers-service/              # Médicos, especialidades, horarios
│   ├── clients-service/                # Aseguradoras, contratos
│   ├── ai-assistant-service/           # Chat con Gemini / LM Studio
│   └── billing-service/                # Facturación (en desarrollo)
├── FrontEnd-Clinica/                   # Astro 6 (en desarrollo)
└── docs/                               # Este documento y referencia de API
```

---

_Documento mantenido junto al código — si la arquitectura cambia, actualizar esta descripción._
