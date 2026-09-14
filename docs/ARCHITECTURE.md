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
        ┌──────────┐ ┌──────────┐ ┌───────────────┐
        │AI Assist │ │ Clinical │ │ Eureka Server │
        │PostgreSQL│ │ History  │ │   Discovery   │
        │          │ │MySQL·S3  │ │               │
        └──────────┘ └──────────┘ └───────────────┘
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
| Cache / Rate Limit| Redis 7                             | Cache distribuida + contador atómico para rate limiting en el gateway           |
| IA                | Spring AI + Gemini API              | Abstracción portable (Gemini en prod, LM Studio en local)                        |
| Contenedores      | Docker + Compose                    | Stack completo levantable con un solo comando                                    |
| Testing           | JUnit 5 + Mockito                   | Pruebas unitarias de servicios, controladores e integraciones                    |
| Build             | Maven multi-módulo                  | Un POM padre gestiona versiones de dependencias para todos los servicios         |

---

## 4. Microservicios — Responsabilidades

> **Nota sobre los puertos.** Los puertos indicados abajo son los del perfil `docker`,
> definidos en el `application.yml` de cada servicio y mapeados en `docker-compose.yml`.
> En el perfil `dev` los servicios de dominio arrancan con `server.port=0` (puerto efímero
> asignado por el sistema operativo) para poder levantar varias instancias en la misma
> máquina sin colisiones; se localizan por nombre lógico a través de Eureka, no por puerto.

### 4.1 Patient Service (`:8081`)

Propietario del **registro administrativo** del paciente: identidad, datos demográficos, contacto,
afiliación en salud, residencia y estado. La historia clínica vive en `clinical-history-service`.

**Arquitectura hexagonal pragmática:**

```
infrastructure/web          PatientController, DTOs de entrada/salida, ETag/If-Match
infrastructure/persistence  JpaPatients (Spring Data), EnversPatientHistory
infrastructure/clients      ResilientHealthProviderDirectory (Feign + Resilience4j + Caffeine)
        │
application                 PatientCommands, PatientQueries
                            puertos: HealthProviderDirectory, PatientHistory
        │
domain                      Patient (métodos de intención, sin setters)
                            IdentityDocument, Demographics, ContactInfo, EmergencyContact,
                            Affiliation, Residence
                            PatientStatus (sellado: Active | Inactive | Deceased)
                            PatientException (sellada)
```

Las dependencias solo apuntan hacia el dominio, y lo verifica un test de ArchUnit. El dominio lleva
anotaciones JPA para no duplicar el modelo; las dependencias externas (`clients-service`, auditoría)
están detrás de puertos.

**Reglas de negocio en el dominio, integridad en la base de datos:**

- El dominio valida la edad según el tipo de documento (solo al registrar o cambiar documento o fecha
  de nacimiento), el contacto de emergencia para menores y las transiciones de estado.
- MySQL solo aplica restricciones deterministas: `CHECK` de formatos, consistencia de afiliación y
  estado, unicidad de documento. No hay triggers.

**Defensa en profundidad:**

| Riesgo                                   | Control                                                   |
| ---------------------------------------- | --------------------------------------------------------- |
| Escritura directa en la base             | Usuario `patient_app` sin `DELETE` ni DDL; `CHECK` en MySQL |
| Otro servicio accede a la base           | Red Docker `patient-data` interna, sin puerto publicado   |
| Datos inválidos por un bug               | Entidad sin setters y valores que se validan al construirse |
| Ediciones concurrentes                   | `@Version` + `If-Match` obligatorio (`412`/`428`)         |
| Cambios sin trazabilidad                 | `createdBy`/`updatedBy` desde el JWT e historial con Envers |

**Eventos para que los demás servicios no dependan de pacientes:** cada cambio del paciente que otros
servicios necesitan se escribe en `patient_outbox.outbox_events` dentro de la misma transacción, y
Debezium lo publica en el topic compactado `patient.events.v1`. `patient-service` no usa Kafka: si
Kafka o Connect caen, sigue registrando y los eventos salen cuando vuelven. El contrato y las reglas
para consumidores están en `patient-service/events/README.md`.

**Resiliencia frente a `clients-service`:** timeout de 1 s de conexión y 2 s de lectura, circuit
breaker, caché local de aseguradoras (5 min) y último valor conocido (24 h). Consultar un paciente
nunca falla por culpa de `clients-service`; registrar con aseguradora responde `503` si no se puede
validar.

**Sin caché de pacientes:** la prueba de carga con 500.000 pacientes a 5 veces el pico de una clínica
de 5.000 pacientes diarios da p95 de 5 ms en lecturas (`patient-service/load-test/README.md`).

**Pacientes sin identificar:** urgencias registra a personas sin documento como `UnidentifiedPatient`
(código de manilla `NN-AAAA-NNNNNN`, sexo, año de nacimiento estimado, descripción). La identidad
tiene un único dueño: cuando se conoce, se vincula con un `Patient` existente o se registra en la
misma transacción, y los consumidores reasignan lo que tengan con el UUID provisional.

**Réplicas:** `docker-compose.yml` levanta dos instancias (`PATIENT_SERVICE_REPLICAS`) que Eureka
balancea; no guardan estado en memoria salvo la caché local de aseguradoras.

### 4.1.1 Plataforma de eventos (Kafka + Debezium)

```
patient-db (MySQL) ──binlog──►┐
admissions-db (PostgreSQL) ──WAL──►  kafka-connect (Debezium) ──► kafka (KRaft) ──► consumidores
<servicio>-db ────────────────►┘            ▲
                                  kafka-connect-init registra los conectores de cada servicio
```

| Componente           | Imagen                                 | Rol                                                     |
| -------------------- | -------------------------------------- | ------------------------------------------------------- |
| `kafka`              | `apache/kafka:4.3.1` (KRaft, 1 nodo)   | Broker; creación automática de topics desactivada        |
| `kafka-connect`      | `quay.io/debezium/connect:3.6.2.Final` | Un solo clúster Connect para todos los servicios         |
| `kafka-connect-init` | `curlimages/curl`                      | Registra (idempotente) cada `*/debezium/*.json` y espera `RUNNING` |
| `kafka-ui`           | `ghcr.io/kafbat/kafka-ui:v1.5.0`       | Solo en `docker-compose.debug.yml` (`127.0.0.1:8090`) |

Para que otro servicio publique eventos basta con: una tabla outbox en su base de datos, un usuario
Debezium de solo lectura sobre esa tabla, su JSON de conector en `<servicio>/debezium/` y un volumen
más en `kafka-connect-init`. Las credenciales del conector se leen como archivos con
`DirectoryConfigProvider` (`${dir:/run/secrets/kafka-connect:<nombre>}`), que renderiza `openbao-agent`;
nunca van en el JSON ni en variables de entorno. Cada conector MySQL necesita un `database.server.id` único.

### 4.1.2 Librerías compartidas (`libs/`)

| Librería                    | Contenido                                                                 |
| --------------------------- | ------------------------------------------------------------------------- |
| `clinica-commons-web`       | `DomainException` + `ErrorCategory`, manejador RFC 9457 con `code` y `traceId`, sin datos de entrada en las respuestas |
| `clinica-commons-security`  | Resource Server JWT RS256 (issuer y tipo `access`), roles, `CurrentUser`, propagación del token en Feign, `AuditorAware`, 401/403 en RFC 9457 |

Son dependencias de compilación con versión fija (`1.0.0`), no servicios: una falla en una versión solo
afecta a los servicios que la adopten.

### 4.1.3 Plataforma de secretos (OpenBao)

```
openbao-bootstrap ─► clave de sello + CA y certificado TLS
openbao-1 ┐
openbao-2 ├─ raft (3 votantes, TLS 1.3, sello estático) ◄── openbao-init: políticas, AppRole, secretos iniciales
openbao-3 ┘        ▲                         ▲
                   │ AppRole + TLS           │ AppRole + TLS
   patient-service / clinical-history-service    openbao-agent ─► archivos para MySQL, Kafka Connect y S3
   (Spring Cloud Vault, perfil openbao)
```

| Componente          | Rol                                                                                   |
| ------------------- | ------------------------------------------------------------------------------------- |
| `openbao-1..3`      | Clúster raft de 3 nodos en la red interna `secrets-net`; tolera la caída de uno        |
| `openbao-bootstrap` | Genera la clave de sello estático y la CA/certificado TLS de desarrollo (idempotente) |
| `openbao-init`      | Inicializa, aplica políticas, crea un AppRole por consumidor y siembra los secretos    |
| `openbao-agent`     | Renderiza en volúmenes dedicados los secretos de los contenedores que no son Spring     |

Los servicios Spring leen sus secretos **una vez al arrancar**: si OpenBao cae después, siguen
funcionando; solo falla el arranque de instancias nuevas mientras no haya nodo activo. Cada consumidor
tiene su propia política y solo lee sus rutas. Operación, rotación y recuperación en
`BackEnd-Clinica/platform/openbao/README.md`.

### 4.2 Clinical History Service (`:8089`)

Propietario de la **historia clínica**: atenciones, notas firmadas, diagnósticos, listas de
antecedentes, signos vitales, anexos y auditoría de lectura. Nunca es dueño de la identidad del
paciente: la recibe por eventos de `patient-service`. Órdenes, resultados y prescripción serán
servicios aparte que referencian la atención por id.

**Arquitectura hexagonal pragmática:**

```
infrastructure/web          EncounterController, NoteController, AttachmentController,
                            PatientChartController, IntegrityController, RecordCopyController,
                            TerminologyController, CareAccessController, EncryptionAdminController
infrastructure/persistence  notas, atenciones, listas vivas, cadena de integridad, outbox
infrastructure/signature    firma SHA-256 del contenido canónico + sello ECDSA P-256
infrastructure/encryption   envelope encryption AES-GCM (DEK por paciente, KEK fuera de la base)
infrastructure/attachments  cliente S3 (AWS SDK v2) sobre almacenamiento con Object Lock
infrastructure/terminology  importador CIE-10 (.xlsx con StAX, idempotente por SHA-256)
infrastructure/messaging    consumidor de patient.events.v1, copia local, DLT
        │
application                 comandos y consultas; puertos ClinicalSignature, DocumentSealer,
                            AttachmentStore, ConceptCatalog, PatientRegistry
        │
domain                      Encounter, Note (BORRADOR → FIRMADA), ClinicalList, VitalSign,
                            ChainLink, TerminologyRelease, excepciones selladas
```

**Solo agregar, nunca reescribir.** Una nota firmada es inmutable: se corrige con una nota
aclaratoria y se deja sin efecto con una anulación motivada, ambas encadenadas a la original. Las
listas vivas (alergias, crónicas, medicación, antecedentes, vacunas) son filas con estado que nunca
se borran y cuyo cambio siempre nace de una nota firmada. El usuario `clinical_app` no tiene
`DELETE` ni DDL; el único esquema con `UPDATE`/`DELETE` es `clinical_workspace`, donde viven los
borradores.

**Firma e integridad.** Al firmar se calcula el SHA-256 de un JSON canónico (claves ordenadas, sin
nulos, `formatVersion 1`), se sella con una clave ECDSA P-256 que vive fuera de la base y se
encadena en `clinical_ledger.chain_links`: apertura de atención, nota, anulación y cierre entran en
una cadena por paciente. `GET /patients/{uuid}/integrity` recalcula la cadena completa sin exponer
contenido clínico. Firmar exige un token emitido hace menos de 15 minutos (`403
RECENT_AUTHENTICATION_REQUIRED`).

**Cifrado.** Todo el contenido narrativo se cifra con AES-GCM usando una DEK por paciente envuelta
por una clave maestra en disco (`CLINICAL_ENCRYPTION_KEYS_LOCATION`); el AAD incluye el propósito y
el identificador del registro, de modo que un bloque cifrado no se puede mover de sitio. La base
añade cifrado InnoDB con `component_keyring_file`, redo, undo y binlog cifrados. El hash de la
cadena se calcula sobre el texto en claro, así que rotar claves no rompe la integridad.

**Anexos.** El archivo se cifra con la DEK del paciente antes de subirlo, viaja a un bucket de
espera sin retención y, al firmar la nota, se copia al bucket de archivo con Object Lock en modo
`COMPLIANCE` hasta la firma + 15 años + 1 día. El SHA-256, el nombre y el tipo forman parte del
contenido sellado de la nota. No hay URLs firmadas: descargar pasa siempre por el servicio, que
verifica acceso, audita y comprueba el hash. Detalle en `clinical-history-service/docs/anexos.md`.

**Acceso.** Rol más relación de cuidado: pertenecer al equipo de la atención, vigente mientras esté
abierta y 30 días después de cerrada. Las notas de categorías sensibles (salud mental, salud sexual,
VIH, violencia) solo las ve su autor y el equipo de esa atención. Romper el vidrio exige un motivo
de al menos 10 caracteres, dura 4 horas y queda cifrado en la base. Cada decisión de acceso
—lecturas, escrituras y rechazos— se publica en `clinical.access-audit.v1`.

**Eventos.** Consume `patient.events.v1` con copia local idempotente por versión y DLT propia;
si `patient-service` cae, las atenciones de pacientes ya conocidos siguen abriéndose y un paciente
desconocido responde `503` sin tumbar el servicio. Publica por outbox dos topics sin compactar:
`clinical.encounters.v1` (hechos y códigos, con la cabeza de la cadena como ancla externa; sin
texto de notas) y `clinical.access-audit.v1` (auditoría de lectura, clave `patientUuid`).

**Catálogos.** El módulo `terminology` importa la tabla oficial CIE-10 del SISPRO desde el `.xlsx`
con un lector StAX propio, idempotente por checksum, y la activación es un paso explícito de
`SUPER_ADMIN`. Las notas firmadas guardan código, descripción y versión del catálogo, de modo que
una versión nueva no reescribe el pasado. Detalle en
`clinical-history-service/docs/catalogo-cie10.md`.

**Copia al paciente.** `POST /patients/{uuid}/record-copies` genera un PDF con toda la historia
—incluidas notas restringidas y anuladas—, los hashes por registro y una página de verificación; el
SHA-256 del PDF se sella con la clave institucional. Es exclusivo del rol `MEDICAL_RECORDS`, exige
motivo y queda auditado. Detalle en `clinical-history-service/docs/copias-historia.md`.

**Carga.** Con 50.000 pacientes en la copia local y el pico de una clínica de 5.000 pacientes
diarios, p95 de lectura y de firma se quedan por debajo de 35 ms
(`clinical-history-service/load-test/README.md`).

**Réplicas:** dos instancias (`CLINICAL_SERVICE_REPLICAS`) sin estado en memoria, en la red interna
`clinical-data` junto a su base y su almacenamiento de anexos.

### 4.3 Admissions Service (`:8083`)

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

### 4.4 Auth Service (`:8086`)

Autenticación centralizada. Emite tokens JWT firmados con RSA-256.

```
AuthController
    ├── AuthenticationService  → Login, logout, refresh token
    ├── UserService            → Registro, cambio de contraseña, bloqueo
    ├── PasswordResetService   → Flujo de reset por token
    └── AuditService           → Registro de todos los eventos de acceso
```

### 4.5 Suppliers Service (`:8085`)

Gestión del personal médico y sus disponibilidades.

```
DoctorController
    └── DoctorService
          ├── DoctorRepository       → Tabla doctors
          ├── ScheduleRepository     → Tabla doctor_schedules
          └── UnavailabilityRepository → Tabla doctor_unavailability
```

### 4.6 Clients Service (`:8087`)

Proveedores de salud: aseguradoras, EPS, redes de clínicas.

```
HealthProviderController
    └── HealthProviderService
          ├── HealthProviderRepository → Tabla health_providers
          ├── ContractRepository       → Tabla contracts
          └── PortfolioRepository      → Tabla portfolios
```

### 4.7 AI Assistant Service (`:8084`)

Asistente conversacional con memoria de sesión e integración con el flujo de admisiones.

```
AIAssistantController
    └── AIAssistantService
          ├── GeminiClient (Spring AI)  → Llamadas a la API de Gemini
          ├── ConversationRepository    → Tabla conversation_history
          └── MessageRepository         → Tabla conversation_messages
```

El asistente detecta intención en la conversación: si el médico describe síntomas de un paciente, puede iniciar automáticamente la creación de una atención llamando al Admissions Service internamente.

### 4.8 API Gateway (`:8080`)

Punto de entrada único para todo el tráfico externo.

```
Cadena de filtros, en orden de ejecución:

  IpRateLimitFilter    → Global, orden 0.  1000 req/min por IP.
                         Va antes de autenticar porque tiene que cubrir el login y el
                         resto de rutas públicas, que es donde ataca quien no tiene
                         credenciales.

  AuthenticationFilter → Por ruta, orden 1. Valida la firma RSA-256 con la clave pública
                         del auth-service, consulta la blacklist en Redis, publica la
                         identidad en un atributo del exchange e inyecta
                         X-User-ID / X-User-Email hacia el servicio destino.

  UserRateLimitFilter  → Global, orden 10. 100 req/min por usuario.
                         Va después de autenticar: la identidad ya está verificada, así
                         que el contador no se puede falsear rotando cabeceras.

  RequestLoggingFilter → Global. Registra método, path, status y tiempo en PostgreSQL
                         (escritura asíncrona en un executor dedicado).

  RouteValidator       → Lista blanca de rutas públicas consultada por
                         AuthenticationFilter (login, public-key, health, swagger).

Los dos límites de tráfico están deliberadamente en filtros distintos porque protegen de
ataques distintos y deben evaluarse en momentos distintos de la cadena. Ver SECURITY.md §5.3.

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
| Clinical History | MySQL 8    | Cifrado InnoDB con keyring; esquemas separados para libro, borradores y claves |
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

Contador de ventana fija implementado con operaciones atómicas de Redis (`INCR` + `EXPIRE`).

```
Por cada request que llega al gateway:
  1. Deriva la clave: rate_limit:ip:<ip> y, si hay token, rate_limit:user:<userId>
  2. INCR sobre la clave (atómico)
  3. Si el contador vale 1 → EXPIRE a 60s (arranca la ventana)
  4. Si el contador <= límite → permite
  5. Si lo supera → 429 Too Many Requests
  6. Al expirar la clave, la ventana se reinicia desde cero
```

Se aplican dos límites independientes: **100 req/min por usuario** y **1000 req/min por IP**.
El de IP existe porque protege endpoints donde todavía no hay usuario autenticado —el login,
sobre todo—; el umbral es más alto porque tras un NAT corporativo hay muchos usuarios
legítimos compartiendo IP.

**Limitación conocida:** una ventana fija tiene el problema del borde — 100 peticiones en el
segundo 59 y otras 100 en el 61 son 200 en dos segundos reales. Un token bucket con recarga
continua lo suavizaría, a costa de un script Lua para mantener la atomicidad.

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
│   ├── libs/                           # clinica-commons-web, clinica-commons-security
│   ├── patient-service/                # Registro administrativo de pacientes
│   ├── clinical-history-service/       # Historia clínica: notas firmadas, anexos, auditoría
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
