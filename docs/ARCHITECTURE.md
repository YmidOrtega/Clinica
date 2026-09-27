# Arquitectura Técnica — Clínica

**Stack:** Java 21 · Spring Boot 3.5.14 · Spring Cloud 2025.0.1
**Dominio:** gestión de una IPS colombiana — pacientes, historia clínica, admisiones, contratación con
pagadores, profesionales, facturación electrónica en salud y revisión de facturas con IA local

---

## 1. Contexto del Problema

Una clínica tiene dominios que cambian a ritmos distintos y con reglas propias: el registro de pacientes
no evoluciona como la facturación ante la DIAN ni como la contratación con las EPS. Cada dominio es un
servicio con su base de datos, y se integran por eventos y por llamadas con el token del usuario.

Requisitos que no se negocian en salud:

- **Trazabilidad**: cada cambio queda con autor y fecha (Envers, cadenas de integridad, auditoría de
  lectura en la historia clínica).
- **Nada se borra por accidente**: el usuario de la aplicación no tiene `DELETE` sobre los datos de
  negocio; lo firmado se corrige agregando, no reescribiendo.
- **Seguridad del personal**: segundo factor obligatorio, tokens de 5 minutos, step-up para lo sensible,
  permisos por rol y datos personales fuera de URLs y logs.
- **Fallos parciales**: si un servicio cae, los demás siguen con su copia local de lo que necesitan y
  responden `503` solo en lo que de verdad depende del caído.

---

## 2. Visión General del Sistema

```
                    navegador (FrontEnd-Clinica)
                               │ cookie de sesión + CSRF
                               ▼
┌───────────────┐   ┌────────────────────┐   ┌─────────────────────────────┐
│ gateway-redis │◄─►│    api-gateway     │──►│ auth-service (OAuth 2.1/OIDC)│
└───────────────┘   │  BFF, rate limit   │   └──────────────┬──────────────┘
                    └─────────┬──────────┘                  │ firma en transit
                              │ Bearer (token del usuario)  ▼
     ┌────────────────────────┼─────────────────────┐  ┌──────────────────┐
     ▼            ▼           ▼          ▼          ▼  │ OpenBao, 3 nodos │
 patient   clinical-history  contracting  practitioners │ KV · transit ·   │
     ▼            ▼           ▼          ▼             │ TOTP · AppRole   │
 admissions ───► billing ───► ai-assistant             └──────────────────┘
     │   Feign con token intercambiado, destino por Eureka (con credenciales)
     ▼
 outbox en cada base ──► Debezium ──► Kafka (KRaft) ──► consumidores con copia local
```

Cada servicio tiene su base en una red Docker interna (`<servicio>-data`) sin puerto publicado; solo el
gateway se publica. `docker-compose.debug.yml` abre los puertos de depuración en `127.0.0.1`.

---

## 3. Stack Tecnológico

| Capa | Tecnología | Por qué |
|---|---|---|
| Runtime | Java 21, hilos virtuales | records, sealed, pattern matching; hilos baratos para E/S |
| Framework | Spring Boot 3.5.14, Spring Cloud 2025.0.1 | actuator, seguridad y configuración maduros |
| Gateway | Spring Cloud Gateway Server MVC como BFF | sesión en servidor, el navegador nunca ve tokens |
| Identidad | Spring Authorization Server, tokens ES256 | auth firma en OpenBao transit sin tener la clave |
| Autorización | `clinica-commons-security` | catálogo de roles y permisos, token exchange (RFC 8693) |
| Secretos | OpenBao 2.6 (KV, transit, TOTP, AppRole) | ningún secreto en variables de entorno ni en archivos del repo |
| Bases de datos | MySQL 8, PostgreSQL 16 | PostgreSQL donde hacen falta restricciones de exclusión (camas) o JSONB |
| Migraciones | Flyway con usuario migrador aparte | la aplicación no tiene DDL |
| Auditoría | Hibernate Envers | historial por entidad con autor de la revisión |
| Eventos | Kafka 4 (KRaft), Debezium 3 con outbox | publicar sin que el servicio hable con Kafka |
| Almacenamiento | MinIO con Object Lock | anexos clínicos inmutables (WORM) |
| Resiliencia | Resilience4j, OpenFeign, Eureka | circuit breaker, timeouts y descubrimiento |
| IA | Spring AI 1.1 + modelo local (LM Studio) | los datos de facturación no salen de la clínica |
| Pruebas | JUnit 5, Testcontainers, WireMock, ArchUnit, k6 | integración contra bases y brokers reales |
| Contenedores | Docker Compose | el stack completo con un comando |

---

## 4. Microservicios — Responsabilidades

> **Arquitectura interna.** Casi todos los servicios son hexagonales pragmáticos: `domain`, `application`
> con puertos solo para lo externo, e `infrastructure`; el dominio lleva anotaciones JPA para no duplicar
> el modelo. `practitioners-service` y `ai-assistant-service` son por capas (`web` → `service` →
> `repository`) para mostrar que también se puede hacer bien. En ambos casos ArchUnit vigila el sentido
> de las dependencias. Los puertos son los del contenedor; los de depuración están en
> `docker-compose.debug.yml`.

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
anotaciones JPA para no duplicar el modelo; las dependencias externas (`contracting-service`, auditoría)
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

**Resiliencia frente a `contracting-service`:** timeout de 1 s de conexión y 2 s de lectura, circuit
breaker, caché local de aseguradoras (5 min) y último valor conocido (24 h). Consultar un paciente
nunca falla por culpa de `contracting-service`; registrar con aseguradora responde `503` si no se puede
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
| `kafka-topics-init`  | `apache/kafka:4.3.1`                   | Crea los topics que alguien consume antes de su primer evento (alertas de billing) |
| `kafka-ui`           | `ghcr.io/kafbat/kafka-ui:v1.5.0`       | Solo en `docker-compose.debug.yml` (`127.0.0.1:8090`) |

Para que otro servicio publique eventos basta con: una tabla outbox en su base de datos, un usuario
Debezium de solo lectura sobre esa tabla, su JSON de conector en `<servicio>/debezium/` y un volumen
más en `kafka-connect-init`. Las credenciales del conector se leen como archivos con
`DirectoryConfigProvider` (`${dir:/run/secrets/kafka-connect:<nombre>}`), que renderiza `openbao-agent`;
nunca van en el JSON ni en variables de entorno. Cada conector MySQL necesita un `database.server.id` único.

### 4.1.2 Librerías compartidas (`libs/`)

| Librería | Versión | Contenido |
|---|---|---|
| `clinica-commons-web` | 1.1.0 | `DomainException` + `ErrorCategory`, manejador RFC 9457 con `code` y `traceId`, ETag e `If-Match` |
| `clinica-commons-openbao` | 1.3.0 | cliente del motor transit de OpenBao (cifrar, descifrar, firmar, versiones y claves públicas); `OpenBaoTestContainer` en su jar de pruebas |
| `clinica-commons-security` | 2.11.1 | resource server de los tokens ES256, catálogo `StaffRole` → `StaffPermission`, revocación desde `auth.users.v1`, step-up, intercambio de tokens para Feign, `AuditorAware`, 401/403 en RFC 9457; `SecurityTestTokens` en su jar de pruebas |
| `clinica-commons-documents` | 1.1.1 | PDF sellados con transit y verificables por un código impreso (comprobantes, copias, representaciones gráficas) |

Son dependencias de compilación con versión fija, no servicios: una versión nueva solo afecta a los
servicios que la adopten. Se compilan en el orden web → openbao → security → documents, y el `pom.xml`
raíz las incluye.

### 4.1.3 Plataforma de secretos (OpenBao)

```
openbao-bootstrap ─► clave de sello + CA y certificado TLS
openbao-1 ┐
openbao-2 ├─ raft (3 votantes, TLS 1.3, sello estático) ◄── openbao-init: políticas, AppRoles, claves de transit, secretos
openbao-3 ┘        ▲                                   ▲
                   │ AppRole + TLS                     │ AppRole + TLS
   los 9 servicios Spring (perfil openbao)      openbao-agent ─► archivos para bases, Kafka Connect, MinIO y Eureka
```

| Componente | Rol |
|---|---|
| `openbao-1..3` | clúster raft de 3 nodos en la red interna `secrets-net`; tolera la caída de uno |
| `openbao-bootstrap` | genera la clave de sello estático y la CA/certificado TLS de desarrollo (idempotente) |
| `openbao-init` | inicializa, aplica políticas, crea un AppRole por consumidor, crea las claves de transit y siembra los secretos |
| `openbao-agent` | renderiza en volúmenes dedicados los secretos de los contenedores que no son Spring |

Los servicios leen sus secretos **una vez al arrancar**: si OpenBao cae después, siguen funcionando y solo
falla el arranque de instancias nuevas. Lo que usa transit en caliente (firmar tokens, sellar notas,
comprobantes y facturas, cifrar la historia clínica) sí depende de OpenBao y responde `503` mientras no
haya nodo activo. Cada consumidor tiene su propia política y solo lee sus rutas: `openbao-e2e.sh`
comprueba que un servicio no lea los secretos de otro. Operación y rotación en
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
infrastructure/signature    firma SHA-256 del contenido canónico + sello ECDSA P-256 en transit
infrastructure/encryption   envelope encryption AES-GCM (DEK por paciente, KEK en transit)
infrastructure/transit      cliente del motor transit de OpenBao (cifrar, descifrar, firmar, versiones)
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
nulos, `formatVersion 1`), se sella con la clave ECDSA P-256 `clinical-seal` del motor transit de
OpenBao, que nunca sale de él, y se encadena en `clinical_ledger.chain_links`: apertura de atención, nota, anulación y cierre entran en
una cadena por paciente. `GET /patients/{uuid}/integrity` recalcula la cadena completa sin exponer
contenido clínico. Firmar exige un segundo factor verificado hace 5 minutos o menos (`401
STEP_UP_REQUIRED`). Para consultar `patient-service` intercambia el token de la persona por uno con
audiencia `patient-service`; si `auth-service` no responde, el paciente se trata como no disponible
(`503`), igual que si `patient-service` estuviera caído.

**Cifrado.** Todo el contenido narrativo se cifra con AES-GCM usando una DEK por paciente que transit
envuelve con la clave `clinical-kek`; el AAD incluye el propósito y el identificador del registro, de
modo que un bloque cifrado no se puede mover de sitio, y la envoltura queda atada al paciente. Sin
OpenBao se sigue leyendo lo que está en caché y se verifica la integridad; firmar y abrir pacientes
nuevos responde `503 CLINICAL_KEYS_UNAVAILABLE`. La base
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

### 4.3 Admissions Service (`:8088`)

Dueño del **episodio**: quién entra, por qué servicio pasa, qué cama ocupa, quién responde por él, qué
autorizó el pagador y cómo sale. No guarda diagnósticos ni notas; la **atención** clínica es de
`clinical-history-service` y la une el `admissionUuid`.

- **Un episodio, varias fases.** Urgencias que termina en hospitalización es un solo episodio (un número,
  una factura) que cambia de fase; cada tipo de servicio dice si exige cama y si la cobertura puede
  bloquear.
- **La cobertura nunca frena una urgencia** (Ley 100, art. 168): sin contrato o con `contracting-service`
  caído, urgencias admite y marca el episodio para facturación; hospitalización y ambulatorio sin
  cobertura se bloquean. Saltarse la cobertura exige permiso y segundo factor.
- **Las camas las cuida PostgreSQL** con una restricción `EXCLUDE USING gist` sobre el rango de ocupación:
  dos personas no pueden asignar la misma cama aunque lleguen en el mismo instante.
- **Triage** reflejado desde la historia clínica (`clinical.encounters.v1`), que es donde lo firma
  enfermería; la cola de urgencias se ordena por nivel.
- **Comprobante sellado**: PDF con la clave `admissions-seal` de transit, verificable sin sesión por su
  código.
- Copias locales de pacientes (`patient.events.v1`) y profesionales (`practitioners.v1`); publica
  `admissions.events.v1`.

Admitir es de recepción (`admissions:admit`); enfermería mueve camas y consulta. Detalle en
`admissions-service/docs/episodio-camas-y-comprobantes.md`.

### 4.4 Auth Service (`:8086`)

Identidad del personal y de los servicios, como servidor OAuth 2.1 / OIDC con Spring Authorization
Server. El catálogo de roles y lo que cada uno puede hacer está en `auth-service/docs/roles-y-permisos.md`.

```
domain/user         User (agregado JPA + Envers), Role, UserStatus, CredentialState y SecondFactorState sellados
domain/secondfactor TotpAuthenticator y RecoveryCodes como puertos, SecondFactorProof sellado, AuthenticationMethod
domain/password     PasswordPolicy (NIST 800-63B-4), PasswordHasher y PasswordDenyList como puertos
domain/throttle     LoginThrottlePolicy, ThrottleKey y LoginAttemptDecision sellados
domain/onetime      enlaces de un solo uso (activación 72 h, reseteo 30 min)
application         LoginFlow, SecondFactorFlow, AccountActivation, PasswordRecovery, AccountMailing, SuperAdminBootstrap,
                    OwnAccount (/me), UserAdministration y UserDirectory (/users), Caller con la regla de step-up
infrastructure      Authorization Server: TransitJwtEncoder (ES256 en OpenBao), TransitJwkSource,
                    aserciones de cliente verificadas contra transit, JdbcAuthorizationStore (tokens
                    hasheados, familias de refresh, 5 sesiones), StepUpFilter (max_age), API JSON del
                    login, TOTP en OpenBao, códigos de recuperación, SMTP, Argon2id; API de usuarios como
                    resource server de sus propios tokens (relee al usuario en cada petición)
```

| Esquema          | Contenido                                                      | Usuario `app`                 |
| ---------------- | -------------------------------------------------------------- | ----------------------------- |
| `auth_db`        | `users`, `recovery_codes` (SHA-256, un solo uso)               | `SELECT`, `INSERT`, `UPDATE`  |
| `auth_history`   | `revisions` (con autor), `users_aud` (Envers)                  | `SELECT`, `INSERT`            |
| `auth_sessions`  | autorizaciones y tokens hasheados, sesiones HTTP, frenado, enlaces, outbox de correo | `SELECT`, `INSERT`, `UPDATE`, `DELETE` |
| `auth_outbox`    | `outbox_events` hacia `auth.users.v1` (compactado) y `auth.security-audit.v1` (sin expiración) | `SELECT`, `INSERT`, `DELETE` |

Depende de OpenBao para firmar tokens, verificar clientes y validar los códigos TOTP del segundo factor
obligatorio: sin OpenBao no hay login ni refresh, pero
los tokens emitidos siguen validándose con el JWKS. Envía correos por SMTP desde un outbox con
reintentos (Mailpit en desarrollo) y, al arrancar sin ningún `SUPER_ADMIN`, invita al configurado en
`secret/auth/bootstrap`. Corre con 2 réplicas; sesiones y autorizaciones viven en MySQL. Publica sus
eventos con el conector `auth-service/debezium/auth-outbox.json` (ver `auth-service/events/README.md`).

### 4.5 Contracting Service (`:8087`)

Lo que se pactó con cada pagador: pagadores (EPS, aseguradoras, entes territoriales), contratos por
modalidad (evento, paquete, capitación, presupuesto global) con su cobertura y CUCON, manuales tarifarios
versionados, excepciones, paquetes y la población capitada.

`POST /api/v1/price-quotes` es la **única fuente de precios** del sistema: resuelve paquete → capitación o
presupuesto → excepción → manual tarifario × factor, y cada línea dice de dónde salió su precio para poder
explicar una factura años después. Lo que no tiene tarifa vuelve como `UNPRICED`. Las decisiones de precio
exigen segundo factor. Publica `contracting.contracts.v1` y `contracting.tariffs.v1`. Detalle en
`contracting-service/docs/`.

### 4.6 Practitioners Service (`:8085`, por capas)

Quién es cada profesional: registro ReTHUS, especialidades del catálogo, tipo de vinculación, el vínculo
opcional con su cuenta de `auth-service` (verificado contra la copia local de `auth.users.v1`, sin
llamarlo) y los acuerdos de honorarios, que nunca se reescriben. Ver honorarios exige
`practitioners:read-fees`. Publica `practitioners.v1`. Detalle en
`practitioners-service/docs/directorio-y-honorarios.md`.

### 4.7 Billing Service (`:8082`)

El ciclo completo de facturación en salud:

```
venta ─► confirmación (precios de contracting) ─► borrador ─► emisión ─► firma XAdES en transit
      ─► DIAN ─► RIPS + CUV del Ministerio (MUV) ─► radicación ante el pagador ─► devoluciones y glosas
```

- **Factura electrónica UBL 2.1** con la extensión del sector salud (DT2 de la Resolución 948 de 2026) y
  firma XAdES-EPES con la clave del certificado importada en transit (no exportable).
- **Copagos y cuotas** facturados al paciente al recaudarlos y descontados de la factura al pagador.
- **Facturación sin contrato** solo en los casos que permite la norma (urgencias, SOAT/ADRES, tutela…),
  con motivo, justificación y segundo factor.
- **RIPS** armado desde la historia clínica y admisiones, validado ante el mecanismo único para obtener
  el CUV; **radicación** dentro de los 22 días hábiles; **notas crédito**; **devoluciones y glosas** con
  el manual único (Resolución 2284) y sus plazos en días hábiles.
- Publica `billing.invoices.v1` (compactado, estado completo) y las alertas de plazos
  `billing.filing-deadlines.v1` y `billing.claim-objections.v1`.

Contra la DIAN y el MUV corre con simuladores WireMock (`platform/simulators`). Detalle en
`billing-service/docs/ciclo-de-facturacion.md`.

### 4.8 AI Assistant Service (`:8084`, por capas)

Asistente de **revisión y control de facturas emitidas** con un modelo local.

```
billing.invoices.v1 ─► copia local ─► reglas deterministas ─► bandeja de hallazgos
alertas de billing ──┘
usuario ─► conversación ─► modelo local (LM Studio) ─► herramientas: copia local + API de billing
                                   │                   con el token intercambiado del usuario
                                   ▼
                           acción propuesta ──(confirmación del usuario)──► billing
```

- Los hallazgos (rechazo DIAN, RIPS sin CUV, plazos de radicación y de glosas, copago faltante…) salen de
  reglas, no del modelo.
- El modelo explica y consulta, pero **solo puede proponer**: firmar, reenviar a la DIAN, enviar el RIPS,
  radicar o responder una glosa. Nada ocurre hasta que el usuario confirma; responder glosas pide segundo
  factor.
- Lo que devuelven las herramientas se trata como dato, nunca como instrucción.
- Conversaciones privadas por usuario, borradas a los 30 días; las acciones quedan como auditoría.

Detalle en `ai-assistant-service/docs/asistente-de-facturas.md`.

### 4.9 API Gateway (`:8080`)

Punto de entrada único del navegador, reconstruido como *backend for frontend* sobre Spring Cloud
Gateway Server WebMVC con hilos virtuales. Contrato para el frontend en `api-gateway/docs/bff.md`.

```
Navegador (frontend en otro origen, CORS con credenciales)
   │ cookie CLINICA_SESSION + cabecera CSRF
   ▼
api-gateway ─ Spring Security: CORS → rate limit por IP → sesión (Spring Session Redis) → CSRF
   │          → oauth2Login con auth-service (authorization code + PKCE, private_key_jwt en transit)
   │          → rate limit por usuario
   ├─ /bff/session, /bff/login, /bff/step-up, /bff/logout
   ├─ /auth/**                         → auth-service (su cookie; X-Forwarded-Prefix /auth)
   └─ /api/v1/**                       → el servicio dueño de la ruta (tabla en api-gateway/docs/bff.md)
                                         con Authorization: Bearer (renovado con candado en Redis)

gateway-redis (red interna gateway-data, contraseña en OpenBao): sesiones, tokens, candados y contadores
```

- No tiene base de datos: el registro de peticiones y la analítica anteriores se eliminaron; quedan
  métricas de Micrometer, trazas y los logs de acceso. La auditoría de accesos clínicos vive en
  `clinical.access-audit.v1` y la de identidad en `auth.security-audit.v1`.
- Sin Redis no hay sesiones: el gateway no puede autenticar a nadie, pero el rate limit falla en abierto.
- Si `auth-service` no responde, las sesiones abiertas siguen con su access token hasta que vence y
  luego reciben `503 AUTH_UNAVAILABLE`; los servicios caídos responden `503 SERVICE_UNAVAILABLE` sin
  cerrar la sesión.
- En Compose las rutas usan los nombres DNS de Docker (`http://auth-service:8086`), que reparten entre
  réplicas; fuera de Docker pueden ser `lb://` con Eureka.
- La verificación pública de documentos sellados (comprobantes de admisión, representaciones gráficas de
  facturas y claves de sellado) pasa sin sesión, con su propio límite por IP.
- Cada ruta espera la respuesta 30 s; la del asistente, 90 s.

---

### 4.10 Mapa de eventos

| Topic | Publica | Consumen |
|---|---|---|
| `patient.events.v1` | patient | clinical-history, admissions |
| `auth.users.v1` | auth | todos (revocación de tokens), practitioners (cuentas) |
| `auth.security-audit.v1` | auth | auditoría |
| `practitioners.v1` | practitioners | clinical-history, admissions |
| `contracting.contracts.v1`, `contracting.tariffs.v1` | contracting | — (disponibles para liquidación) |
| `admissions.events.v1` | admissions | billing |
| `clinical.encounters.v1` | clinical-history | admissions (triage), billing (RIPS) |
| `clinical.access-audit.v1` | clinical-history | auditoría |
| `billing.invoices.v1`, `billing.filing-deadlines.v1`, `billing.claim-objections.v1` | billing | ai-assistant |

Cada productor documenta el contrato en `<servicio>/events/` (JSON Schema), y sus consumidores validan
en sus pruebas los ejemplos contra ese esquema. Cada consumidor tiene su dead letter topic
(`<topic>.<consumidor>.dlt`).

---

## 5. Service Discovery — Eureka

Los servicios se registran en Eureka y los clientes Feign entre servicios (billing → admissions, el
asistente → billing, clinical-history → patient…) eligen el destino con él y reparten entre réplicas. El
gateway, en cambio, enruta por el nombre DNS de Docker.

**Registro cerrado.** Esos clientes envían el token intercambiado del usuario, así que quien pudiera
registrarse como `billing-service` recibiría esos tokens. Por eso Eureka exige credenciales para leer y
para registrarse: cada servicio las toma de OpenBao (`secret/eureka/client`) con su AppRole, y el servidor
las recibe del agente en un volumen propio. Solo `/actuator/health` e `/info` quedan abiertos, y el puerto
se publica únicamente en `docker-compose.debug.yml` y en `127.0.0.1`.

---

## 6. Estrategia de Base de Datos

Cada servicio es dueño exclusivo de su base; nadie hace JOIN con otra. Lo que un servicio necesita de
otro lo recibe por eventos y lo guarda en una copia local, o lo pide por API.

| Servicio | Motor | Esquemas y notas |
|---|---|---|
| auth | MySQL 8 | usuarios, historial, sesiones y tokens hasheados, outbox |
| patient | MySQL 8 | pacientes, historial Envers, outbox |
| clinical-history | MySQL 8 | libro de integridad, borradores, claves envueltas; cifrado InnoDB con keyring |
| contracting | MySQL 8 | pagadores, contratos, manuales versionados, outbox |
| practitioners | MySQL 8 | directorio, catálogo de especialidades, honorarios, outbox |
| admissions | PostgreSQL 16 | episodios y camas con `EXCLUDE USING gist`, outbox por WAL |
| billing | MySQL 8 | ventas, facturas, documentos electrónicos, RIPS, glosas, outbox |
| ai-assistant | PostgreSQL 16 | copia de facturas en JSONB, hallazgos, conversaciones y acciones |

**Dos usuarios por base.** El migrador ejecuta Flyway y es dueño del esquema; la aplicación solo lee,
inserta y actualiza, sin `DELETE` ni DDL, y lo comprueba un `DatabaseAccessIT` en cada servicio con base
(salvo practitioners, que todavía no lo tiene). Las
excepciones están acotadas: los borradores clínicos, el outbox (que Debezium vacía) y la purga de
conversaciones del asistente, que corre por una función `SECURITY DEFINER` y no por un permiso.

**Flyway** corre al arrancar con el usuario migrador; las migraciones nunca se editan después de aplicadas.

---

## 7. Patrones de Resiliencia

- **Timeouts y circuit breaker** en cada cliente Feign (Resilience4j). Abierto el circuito, se responde
  `503` con un código propio (`CONTRACTING_UNAVAILABLE`, `MINISTRY_VALIDATOR_UNAVAILABLE`…) sin esperar el
  timeout.
- **Copias locales** de lo que se consulta en cada petición (pacientes, profesionales, facturas), para que
  la caída del dueño no tumbe al consumidor.
- **Degradar en vez de fallar** donde la norma lo exige: urgencias admite aunque contracting no responda;
  billing deja la factura emitida y en cola si la DIAN o el Ministerio no contestan, y reintenta.
- **Reintentos con backoff** en los consumidores de Kafka (4 intentos, exponencial desde 500 ms) antes de
  mandar el mensaje a su dead letter topic; los mensajes mal formados van directo, sin reintentar.
- **Rate limit en el gateway** con ventana fija en Redis: 1000 peticiones por minuto por IP antes de
  autenticar, 300 por usuario y 60 por IP en las rutas públicas sin sesión (verificación de documentos).
  Si Redis cae, el límite deja pasar y lo registra.
- **Timeouts del gateway**: 30 s por defecto y 90 s para el asistente, cuyo modelo local puede tardar.

---

## 8. Decisiones de Diseño Clave

### 8.1 JWT ES256 firmado en OpenBao en lugar de HMAC o de una clave en archivo

**Decisión:** `auth-service` firma los tokens con ECDSA P-256 en el motor transit de OpenBao; los servicios validan con las claves públicas del JWKS.
**Por qué:** con HMAC todos los servicios compartirían el secreto que permite emitir tokens. Con una clave privada en archivo, quien comprometa `auth-service` se la lleva (la clave RS256 anterior llegó a publicarse en el repositorio). En transit la clave nunca sale de OpenBao, rota sola cada 30 días y cada firma queda auditada.

### 8.2 Backend for frontend en el gateway

**Decisión:** el navegador solo tiene una cookie de sesión; el gateway guarda los tokens en Redis y los renueva.
**Por qué:** un token en el navegador queda expuesto a XSS. Con el BFF, el access token vive 5 minutos, nunca toca JavaScript y cerrar la sesión revoca el refresh token en `auth-service`.

### 8.3 Token exchange entre servicios

**Decisión:** cuando un servicio llama a otro, intercambia el token del usuario por uno con la audiencia del destino (RFC 8693).
**Por qué:** el servicio llamado aplica los permisos de la persona, no los de un servicio con acceso total. El token intercambiado conserva `auth_time` y el segundo factor, así que el step-up funciona de punta a punta.

### 8.4 Outbox con Debezium en lugar de publicar desde el servicio

**Decisión:** los eventos se escriben en una tabla outbox en la misma transacción del cambio y Debezium los publica.
**Por qué:** publicar en Kafka dentro de la transacción no es atómico; o se pierde el evento o se publica uno que no ocurrió. Con outbox, si Kafka cae el servicio sigue funcionando y los eventos salen después.

### 8.5 La aplicación no borra

**Decisión:** el usuario de base de datos de la aplicación no tiene `DELETE` ni DDL; no hay borrado lógico con `deleted_at`.
**Por qué:** un bug o un atacante con la conexión de la aplicación no puede destruir registros. Lo que termina se modela como estado (inactivo, anulado, revocado) y lo firmado se corrige agregando: una nota aclaratoria, una nota crédito, una revocación.

### 8.6 Reglas en el dominio, integridad en la base

**Decisión:** las reglas de negocio viven en el dominio; la base solo aplica restricciones deterministas (`CHECK`, unicidad, exclusión) y no hay triggers.
**Por qué:** una regla en un trigger no se prueba ni se lee con el resto del código. Los `CHECK` quedan como segunda línea de defensa contra escrituras que no pasen por la aplicación.

### 8.7 Una base de datos por servicio

**Decisión:** no hay tablas compartidas entre microservicios. Las referencias cruzadas se hacen por UUID.
**Por qué:** el acoplamiento a nivel de esquema es el más peligroso en microservicios: un cambio de columna rompe a todos los que hacen JOIN. Con bases independientes, cada servicio evoluciona su esquema solo.

---

## 9. Estructura del Proyecto

```
Clinica/
├── BackEnd-Clinica/
│   ├── pom.xml                     # POM padre: versiones de dependencias y módulos
│   ├── docker-compose.yml          # stack completo; solo publica el gateway
│   ├── docker-compose.debug.yml    # puertos de depuración en 127.0.0.1 y simuladores
│   ├── libs/                       # web, openbao, security, documents
│   ├── api-gateway/                # BFF
│   ├── auth-service/               # OAuth 2.1 / OIDC, cuentas y TOTP
│   ├── patient-service/            # registro administrativo de pacientes
│   ├── clinical-history-service/   # historia clínica firmada, anexos y auditoría
│   ├── contracting-service/        # pagadores, contratos, tarifas y precios
│   ├── practitioners-service/      # directorio profesional y honorarios
│   ├── admissions-service/         # episodios, camas, cobertura y comprobantes
│   ├── billing-service/            # facturación electrónica en salud
│   ├── ai-assistant-service/       # revisión de facturas con modelo local
│   ├── eureka-service/             # registro de servicios con credenciales
│   └── platform/                   # openbao, kafka, kafka-connect, simuladores y E2E
├── FrontEnd-Clinica/               # Astro (base del proyecto)
└── docs/                           # este documento, seguridad, API y variables
```

Cada servicio tiene `docs/` con sus reglas de negocio, `events/` con los contratos que publica (si
publica), `debezium/` con su conector y, si aplica, `load-test/` con su prueba de carga k6.

---

_Documento mantenido junto al código: si la arquitectura cambia, se actualiza aquí._
