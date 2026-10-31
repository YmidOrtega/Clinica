# Clínica

**A management platform for a Colombian healthcare provider (IPS), built as microservices.** Patient
registry, signed clinical records, admissions and beds, payer contracts, a practitioner directory,
electronic health invoicing to the DIAN and the Ministry of Health, and an invoice review assistant that
runs on a local language model.

[![Backend CI](https://github.com/YmidOrtega/Clinica/actions/workflows/backend-ci.yml/badge.svg)](https://github.com/YmidOrtega/Clinica/actions/workflows/backend-ci.yml)
[![Java](https://img.shields.io/badge/Java-21-orange?logo=openjdk)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.14-6DB33F?logo=springboot)](https://spring.io/projects/spring-boot)
[![Spring Cloud](https://img.shields.io/badge/Spring%20Cloud-2025.0.1-6DB33F?logo=spring)](https://spring.io/projects/spring-cloud)
[![License](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)

> Portfolio project. It implements current Colombian regulations (health e-invoicing, RIPS under
> Resolution 948 of 2026, the single glosa manual) against simulators. It is not registered with the DIAN
> or the Ministry, and it is not certified to handle real patient data.

## Measured, not claimed

Every service on a hot path has a k6 load test with its own thresholds. Results at the expected peak (×1)
and above it, 0 % errors in every run:

| Service | What runs | p95 at ×1 | Highest load measured |
|---|---|---|---|
| `auth-service` | Shift change for 1,500 staff with TOTP, refresh and token exchange | full login 303 ms, refresh 60 ms | ×2: 185 req/s, full login 617 ms |
| `clinical-history-service` | 5,000 patients a day; every note encrypted, hashed, ECDSA-sealed and chained | signing a note 32.6 ms | ×10: 255 req/s, signing 40.1 ms |
| `patient-service` | 500,000 patients in MySQL, no cache | read by UUID 10.4 ms | ×5: 137 req/s, read 5.0 ms |
| `billing-service` | Reads, charges and invoices issued end to end with a XAdES signature in OpenBao | issuing 116 ms | ×5: 11,099 requests, issuing 84 ms |
| `contracting-service` | Price resolution, contracts, payers and capitation coverage | 13–18 ms | ×10: 250 price lookups/s, 8–10 ms |
| `practitioners-service` | Profile, search by specialty, catalog, account lookup | 17–18 ms | ×5: 100 profile reads/s, 13–14 ms |

Measured between 2026-09-13 and 2026-09-15 on a 16-core machine with 61 GB of RAM, with the stack in
Docker and no resource limits. Each `load-test/README.md` has the load model, the full table and what it
found: the first auth run deadlocked the connection pool (fixed, with a one-connection test so it stays
fixed), and name search was rewritten after an `ngram` FULLTEXT index hit a p95 of 1,895 ms (now 37 ms).
Billing issued 109 invoices across both runs with no repeated or missing number.

## Run it

Docker with Compose v2, Java 21 and Maven 3.9. The E2E scripts also need Node.js 22, `jq`, `curl` and
`openssl`.

```bash
git clone https://github.com/YmidOrtega/Clinica.git
cd Clinica/BackEnd-Clinica

# 1. Build the shared libraries and the services
mvn install -DskipTests

# 2. Start the stack with debug ports and the simulators (DIAN, MUV and the local model)
docker compose -f docker-compose.yml -f docker-compose.debug.yml up -d --build \
  api-gateway auth-service patient-service clinical-history-service contracting-service \
  practitioners-service admissions-service billing-service ai-assistant-service \
  dian-simulator muv-simulator llm-simulator kafka-connect-init mailpit

# 3. Wait until everything is healthy
docker compose ps
```

No `.env` needed. OpenBao initializes itself: it generates the database passwords, signing keys and
AppRoles, and each service reads its own at startup. The first user is `superadmin@clinica.local`, and its
activation link arrives in Mailpit.

To use a real model instead of the simulator, load a model with tool calling in LM Studio (for example
`qwen3-8b`) and set `AI_ASSISTANT_LLM_BASE_URL=http://host.docker.internal:1234`.

| Interface | Address |
|---|---|
| API (gateway) | http://localhost:8080 |
| Mailpit (activation emails) | http://127.0.0.1:8025 |
| Kafka UI | http://127.0.0.1:8090 |
| Eureka (with the credential in `secret/eureka/client`) | http://127.0.0.1:8761 |
| Swagger for each service | `http://127.0.0.1:<port>/swagger-ui.html`, ports in `docker-compose.debug.yml` |

Without `docker-compose.debug.yml`, only the gateway is published.

## Services

| Service | What it does | Style | Data |
|---|---|---|---|
| `api-gateway` | Backend for frontend: cookie session, OIDC login, CSRF, rate limiting, routing with the user's token | hexagonal | Redis |
| `auth-service` | OAuth 2.1 / OIDC server, staff accounts, mandatory TOTP, step-up and token exchange between services | hexagonal | MySQL |
| `patient-service` | Identity, contact, insurance affiliation and status, including unidentified patients | hexagonal | MySQL |
| `clinical-history-service` | Encounters, signed notes, ICD-10, vital signs, encrypted attachments on WORM storage, sealed copies | hexagonal | MySQL + MinIO |
| `contracting-service` | Payers, contracts, tariff manuals, capitation and price quotes | hexagonal | MySQL |
| `practitioners-service` | Practitioner directory, specialties and fee agreements | layered | MySQL |
| `admissions-service` | Care episodes, coverage, authorizations, beds, triage and sealed receipts | hexagonal | PostgreSQL |
| `billing-service` | Sales, signed e-invoices, DIAN, RIPS and CUV, filing, credit notes and glosas | hexagonal | MySQL |
| `ai-assistant-service` | Findings inbox over issued invoices and a chat with a local model that can only propose actions | layered | PostgreSQL |
| `eureka-service` | Service registry, locked with credentials | — | — |

Shared libraries in `BackEnd-Clinica/libs`, with fixed versions (no SNAPSHOT): `clinica-commons-web`
(ProblemDetail errors and ETags), `clinica-commons-security` (token validation, the role and permission
catalog, delegation between services), `clinica-commons-openbao` (OpenBao transit client) and
`clinica-commons-documents` (sealed, verifiable PDFs).

## Architecture

```
                         browser (FrontEnd-Clinica, Astro)
                                    │ session cookie + CSRF
                            ┌───────▼────────┐        ┌───────────────┐
                            │  api-gateway   │◄──────►│ gateway-redis │
                            │  (BFF, :8080)  │        └───────────────┘
                            └───┬────────┬───┘
                   OIDC login   │        │ Bearer with the user's token
            ┌───────────────────▼┐   ┌───▼─────────────────────────────────────────────┐
            │    auth-service    │   │ patient · clinical-history · contracting ·      │
            │  OAuth 2.1 + TOTP  │   │ practitioners · admissions · billing · assistant │
            └─────────┬──────────┘   └───┬─────────────┬────────────────────┬──────────┘
                      │ ES256 signing    │ Feign with  │ transactional      │ one database
                      │ in transit       │ exchanged   │ outbox             │ per service, on
            ┌─────────▼──────────┐       │ token       ▼                    ▼ an internal network
            │ OpenBao (3 nodes)  │   ┌───▼─────────┐ ┌──────────┐    ┌─────────────────┐
            │ secrets, transit,  │   │   Eureka    │ │ Debezium │───►│  Kafka (KRaft)  │
            │ TOTP and AppRoles  │   │ (with auth) │ └──────────┘    │ JSON contracts  │
            └────────────────────┘   └─────────────┘                 └─────────────────┘
```

- **The browser never sees a token.** The gateway keeps the session in Redis and forwards a 5-minute access
  token, signed by auth-service (ES256) with an OpenBao transit key that cannot be exported.
- **Two factors for everyone, step-up for what matters.** All staff use TOTP. Signing clinical notes,
  issuing invoices and accepting a glosa also require recent authentication (step-up). Permissions are
  fine-grained per role (`billing:invoice`, `admissions:admit`, `assistant:use`…).
- **The user's permissions travel with the call.** Between services, each request carries the user's token
  exchanged for the target (RFC 8693). Each service logs into OpenBao with its own AppRole and reads only
  its own secrets; Eureka requires credentials so no one can register as another service.
- **Each service owns its database**, on an internal Docker network with no published port, with two
  users: the migration user (Flyway, DDL) and the application user, which cannot delete rows or change the
  schema. Hibernate Envers audits changes, `If-Match`/ETag guards concurrent edits, URLs use UUIDs, and
  searches by ID document go through `POST /search` so personal data stays out of the logs.
- **No service publishes to Kafka directly.** Events go through a transactional outbox and Debezium, to
  compacted topics with dead letter topics and JSON Schema contracts that consumers validate in their tests.
  Synchronous calls have circuit breakers, and hot reference data is kept in a local copy.
- **Colombian health billing.** UBL 2.1 e-invoices with the health sector extension and XAdES-EPES
  signatures; RIPS in JSON validated by the Ministry's single mechanism to get the CUV; filing within 22
  business days, credit notes, and returns and glosas under the single manual (Resolution 2284).
- **AI that stays in the clinic.** The assistant uses Spring AI against LM Studio (OpenAI-compatible API),
  so billing data never leaves. Findings come from deterministic rules; the model explains them and queries
  invoices through typed tools, but it can only propose actions, and the user confirms them.

## Stack

| Layer | Technology |
|---|---|
| Language and framework | Java 21, Spring Boot 3.5.14, Spring Cloud 2025.0.1, Spring Security, Spring AI 1.1 |
| Gateway | Spring Cloud Gateway Server MVC as a BFF, Spring Session on Redis |
| Data | MySQL 8, PostgreSQL 16, Flyway, Hibernate Envers, MinIO for WORM attachments |
| Events | Apache Kafka 4 (KRaft), Debezium 3 with outbox |
| Secrets and cryptography | OpenBao 2.6, 3-node cluster (KV, transit, TOTP, AppRole) |
| Resilience and discovery | Resilience4j, Spring Cloud OpenFeign, Eureka |
| Testing | JUnit 5, Testcontainers, WireMock, ArchUnit, JSON Schema, k6, shell E2E on the full stack |
| Infrastructure | Docker Compose, GitHub Actions |

## Testing

```bash
cd BackEnd-Clinica/billing-service
mvn verify        # unit + integration with Testcontainers (needs Docker)
```

Every service has unit tests, integration tests against real databases, Kafka and OpenBao in containers,
and ArchUnit architecture rules. On the running stack:

```bash
cd BackEnd-Clinica
sh platform/e2e/auth-e2e.sh            # login, TOTP, sessions and revocation
sh platform/e2e/gateway-e2e.sh         # BFF session, login, token relay and step-up
sh platform/e2e/clinical-e2e.sh        # patients and clinical records
sh platform/e2e/contracting-e2e.sh     # payers, manuals and contracts
sh platform/e2e/practitioners-e2e.sh   # directory and linked accounts
sh platform/e2e/admissions-e2e.sh      # episodes, beds and receipts
sh platform/e2e/billing-e2e.sh         # from sale to glosa, with simulated DIAN and MUV
sh platform/e2e/assistant-e2e.sh       # invoice assistant with the simulated model
sh platform/e2e/openbao-e2e.sh         # cluster failover and secret isolation
```

CI (`.github/workflows/backend-ci.yml`) tests the libraries and every service on each push, and runs the
full E2E suite nightly or on demand. CodeQL and dependency review run on pull requests. The k6 load tests
live in each service's `load-test` folder.

## Project layout

```
Clinica/
├── BackEnd-Clinica/
│   ├── api-gateway/  auth-service/  patient-service/  clinical-history-service/
│   ├── contracting-service/  practitioners-service/  admissions-service/
│   ├── billing-service/  ai-assistant-service/  eureka-service/
│   ├── libs/                  shared libraries with fixed versions
│   ├── platform/
│   │   ├── openbao/           policies, agent and cluster provisioning
│   │   ├── kafka/  kafka-connect/  minio/
│   │   ├── simulators/        WireMock for the DIAN, the MUV and the local model
│   │   └── e2e/               end-to-end tests
│   ├── docker-compose.yml
│   └── docker-compose.debug.yml
├── FrontEnd-Clinica/          Astro; only the project scaffold for now
└── docs/                      architecture, security, API and environment variables
```

Each service documents its business rules in its own `docs/` folder, and services that publish events keep
their contracts in `events/`.

## Documentation

The documentation is written in Spanish.

| Document | Content |
|---|---|
| [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) | Services, boundaries, events and design decisions |
| [`docs/SECURITY.md`](docs/SECURITY.md) | Sessions, tokens, OpenBao, permissions and data protection |
| [`docs/API_DOCUMENTATION.md`](docs/API_DOCUMENTATION.md) | REST API through the gateway |
| [`docs/variablesDeEntorno.md`](docs/variablesDeEntorno.md) | Environment variables and secrets |
| [BFF](BackEnd-Clinica/api-gateway/docs/bff.md) | Gateway sessions, CSRF and routing |
| [Login](BackEnd-Clinica/auth-service/docs/flujo-de-login.md), [roles and permissions](BackEnd-Clinica/auth-service/docs/roles-y-permisos.md), [operations](BackEnd-Clinica/auth-service/docs/operacion.md) | auth-service |
| [Keys and encryption](BackEnd-Clinica/clinical-history-service/docs/claves-y-cifrado.md), [attachments](BackEnd-Clinica/clinical-history-service/docs/anexos.md) | clinical-history-service |
| [Price resolution](BackEnd-Clinica/contracting-service/docs/resolucion-de-precios.md) | contracting-service |
| [Directory and fees](BackEnd-Clinica/practitioners-service/docs/directorio-y-honorarios.md) | practitioners-service |
| [Episodes, beds and receipts](BackEnd-Clinica/admissions-service/docs/episodio-camas-y-comprobantes.md) | admissions-service |
| [Billing cycle](BackEnd-Clinica/billing-service/docs/ciclo-de-facturacion.md) | billing-service |
| [Invoice assistant](BackEnd-Clinica/ai-assistant-service/docs/asistente-de-facturas.md) | ai-assistant-service |

## License

MIT. See [LICENSE](LICENSE).

---

<div align="center">

**Built with ☕ Java and 🩺 Healthcare Engineering**

**by [Ymid Ortega](https://github.com/YmidOrtega)**

[![GitHub](https://img.shields.io/badge/GitHub-YmidOrtega-181717?logo=github)](https://github.com/YmidOrtega)
[![LinkedIn](https://img.shields.io/badge/LinkedIn-Connect-0077B5?logo=linkedin)](https://linkedin.com/in/ymidortega)

*If you found this project useful, consider giving it a ⭐!*

</div>
