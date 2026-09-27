# Clínica

Plataforma de gestión para una IPS colombiana construida con microservicios: registro de pacientes,
historia clínica firmada, admisiones y camas, contratación con pagadores, directorio de profesionales,
facturación electrónica en salud ante la DIAN y el Ministerio, y un asistente de revisión de facturas
que corre con un modelo de lenguaje local.

[![Backend CI](https://github.com/YmidOrtega/Clinica/actions/workflows/backend-ci.yml/badge.svg)](https://github.com/YmidOrtega/Clinica/actions/workflows/backend-ci.yml)
![Java 21](https://img.shields.io/badge/Java-21-orange)
![Spring Boot 3.5](https://img.shields.io/badge/Spring%20Boot-3.5.14-brightgreen)
![Spring Cloud 2025.0](https://img.shields.io/badge/Spring%20Cloud-2025.0.1-brightgreen)
[![Licencia MIT](https://img.shields.io/badge/licencia-MIT-blue)](LICENSE)

> Proyecto de portafolio. Implementa la normativa colombiana vigente (FEV en salud, RIPS de la
> Resolución 948 de 2026, manual único de glosas) contra simuladores; no está habilitado ante la DIAN ni
> el Ministerio ni certificado para operar con datos reales de pacientes.

---

## Servicios

| Servicio | Qué hace | Arquitectura | Datos | Documentación |
|---|---|---|---|---|
| `api-gateway` | Backend for frontend: sesión en cookie, login OIDC, CSRF, rate limit y enrutamiento con el token del usuario | hexagonal | Redis (sesiones) | [bff](BackEnd-Clinica/api-gateway/docs/bff.md) |
| `auth-service` | Servidor OAuth 2.1 / OIDC, cuentas del personal, TOTP obligatorio, step-up y token exchange entre servicios | hexagonal | MySQL | [login](BackEnd-Clinica/auth-service/docs/flujo-de-login.md), [roles y permisos](BackEnd-Clinica/auth-service/docs/roles-y-permisos.md), [operación](BackEnd-Clinica/auth-service/docs/operacion.md) |
| `patient-service` | Identidad, contacto, afiliación y estado del paciente, incluidos los no identificados (NN) | hexagonal | MySQL | — |
| `clinical-history-service` | Atenciones, notas firmadas, CIE-10, signos vitales, anexos cifrados en almacenamiento WORM y copias selladas | hexagonal | MySQL + MinIO | [claves y cifrado](BackEnd-Clinica/clinical-history-service/docs/claves-y-cifrado.md), [anexos](BackEnd-Clinica/clinical-history-service/docs/anexos.md) |
| `contracting-service` | Pagadores, contratos, manuales tarifarios, capitación y cotización de precios | hexagonal | MySQL | [resolución de precios](BackEnd-Clinica/contracting-service/docs/resolucion-de-precios.md) |
| `practitioners-service` | Directorio de profesionales, especialidades y acuerdos de honorarios | por capas | MySQL | [directorio y honorarios](BackEnd-Clinica/practitioners-service/docs/directorio-y-honorarios.md) |
| `admissions-service` | Episodios de atención, cobertura, autorizaciones, camas, triage y comprobantes sellados | hexagonal | PostgreSQL | [episodio, camas y comprobantes](BackEnd-Clinica/admissions-service/docs/episodio-camas-y-comprobantes.md) |
| `billing-service` | Ventas, factura electrónica firmada, DIAN, RIPS y CUV, radicación, notas crédito y glosas | hexagonal | MySQL | [ciclo de facturación](BackEnd-Clinica/billing-service/docs/ciclo-de-facturacion.md) |
| `ai-assistant-service` | Bandeja de hallazgos sobre facturas emitidas y chat con un modelo local que solo propone acciones | por capas | PostgreSQL | [asistente de facturas](BackEnd-Clinica/ai-assistant-service/docs/asistente-de-facturas.md) |
| `eureka-service` | Registro de servicios, cerrado con credenciales | — | — | — |

Librerías compartidas en `BackEnd-Clinica/libs`, con versión fija (no SNAPSHOT):

- `clinica-commons-web`: errores en ProblemDetail y ETag.
- `clinica-commons-security`: validación de tokens, catálogo de roles y permisos, delegación entre servicios.
- `clinica-commons-openbao`: cliente de OpenBao transit.
- `clinica-commons-documents`: PDF sellados y verificables.

---

## Arquitectura

```
                         navegador (FrontEnd-Clinica, Astro)
                                    │ cookie de sesión + CSRF
                            ┌───────▼────────┐        ┌───────────────┐
                            │  api-gateway   │◄──────►│ gateway-redis │
                            │  (BFF, :8080)  │        └───────────────┘
                            └───┬────────┬───┘
                   login OIDC   │        │ Bearer con el token del usuario
            ┌───────────────────▼┐   ┌───▼─────────────────────────────────────────────┐
            │    auth-service    │   │ patient · clinical-history · contracting ·      │
            │  OAuth 2.1 + TOTP  │   │ practitioners · admissions · billing · assistant │
            └─────────┬──────────┘   └───┬─────────────┬────────────────────┬──────────┘
                      │ firma ES256      │ Feign con   │ outbox             │ una base por
                      │ en transit       │ token       │ transaccional      │ servicio, en su
            ┌─────────▼──────────┐       │ intercambiado▼                    ▼ red interna
            │ OpenBao (3 nodos)  │   ┌───▼─────────┐ ┌──────────┐    ┌─────────────────┐
            │ secretos, transit, │   │   Eureka    │ │ Debezium │───►│  Kafka (KRaft)  │
            │ TOTP y AppRoles    │   │ (con clave) │ └──────────┘    │ contratos JSON  │
            └────────────────────┘   └─────────────┘                 └─────────────────┘
```

**Seguridad**
- El navegador nunca ve un token. El gateway guarda la sesión en Redis y reenvía a los servicios un
  access token de 5 minutos, que auth-service firma (ES256) con una clave de OpenBao transit que no se
  puede exportar.
- Todo el personal usa segundo factor TOTP. Lo sensible pide además autenticación reciente (step-up):
  firmar notas clínicas, emitir facturas, aceptar valor en una glosa.
- Los permisos son finos y por rol (`billing:invoice`, `admissions:admit`, `assistant:use`…), definidos en
  una librería compartida.
- Entre servicios, cada llamada lleva el token del usuario intercambiado para el destino (RFC 8693), así
  que el servicio que recibe aplica los permisos de esa persona.
- Cada servicio entra a OpenBao con su propio AppRole y solo lee sus secretos. Eureka exige credenciales
  para que nadie se registre como otro servicio.

**Datos**
- Cada servicio es dueño de su base, en una red Docker interna sin puerto publicado.
- Cada base tiene dos usuarios: el de migraciones (Flyway, DDL) y el de la aplicación, que no puede borrar
  filas ni cambiar el esquema.
- Auditoría con Hibernate Envers y concurrencia optimista con `If-Match`/ETag.
- Las URLs llevan UUID y las búsquedas por documento van por `POST /search`, para que los datos
  personales no queden en los logs.

**Integración**
- Eventos con outbox transaccional y Debezium: ningún servicio publica directo en Kafka.
- Topics compactados con el estado completo, dead letter topics, y contratos JSON Schema que los
  consumidores validan en sus pruebas.
- Las llamadas síncronas llevan circuit breaker, y lo que se consulta seguido se guarda en una copia
  local.

**Normativa colombiana en billing**
- Factura electrónica UBL 2.1 con la extensión del sector salud y firma XAdES-EPES.
- RIPS en JSON validado ante el mecanismo único del Ministerio para obtener el CUV.
- Radicación dentro de los 22 días hábiles, notas crédito, y devoluciones y glosas según el manual único
  (Resolución 2284).

**IA local**
- El asistente usa Spring AI contra LM Studio (API compatible con OpenAI): los datos de facturación no
  salen de la clínica.
- Los hallazgos salen de reglas deterministas. El modelo los explica y consulta las facturas con
  herramientas tipadas, pero solo puede proponer acciones: el usuario las confirma.

Más detalle en [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) y [docs/SECURITY.md](docs/SECURITY.md).

---

## Stack

| | |
|---|---|
| Lenguaje y framework | Java 21, Spring Boot 3.5.14, Spring Cloud 2025.0.1, Spring Security, Spring AI 1.1 |
| Gateway | Spring Cloud Gateway Server MVC como BFF, Spring Session sobre Redis |
| Datos | MySQL 8, PostgreSQL 16, Flyway, Hibernate Envers, MinIO para anexos WORM |
| Eventos | Apache Kafka 4 (KRaft), Debezium 3 con outbox |
| Secretos y criptografía | OpenBao 2.6 en clúster de 3 nodos (KV, transit, TOTP, AppRole) |
| Resiliencia y descubrimiento | Resilience4j, Spring Cloud OpenFeign, Eureka |
| Pruebas | JUnit 5, Testcontainers, WireMock, ArchUnit, JSON Schema, k6 y E2E en shell sobre el stack completo |
| Infraestructura | Docker Compose, GitHub Actions |

---

## Levantar el proyecto

**Requisitos:** Docker con Compose v2, Java 21, Maven 3.9 y, para los E2E, Node.js 22, `jq`, `curl` y
`openssl`.

```bash
git clone https://github.com/YmidOrtega/Clinica.git
cd Clinica/BackEnd-Clinica

# 1. Compilar librerías y servicios
mvn install -DskipTests

# 2. Levantar el stack con los puertos de depuración y los simuladores (DIAN, MUV y modelo local)
docker compose -f docker-compose.yml -f docker-compose.debug.yml up -d --build \
  api-gateway auth-service patient-service clinical-history-service contracting-service \
  practitioners-service admissions-service billing-service ai-assistant-service \
  dian-simulator muv-simulator llm-simulator kafka-connect-init mailpit

# 3. Esperar a que todo esté sano
docker compose ps
```

No hace falta un `.env`. OpenBao se inicializa solo: genera las contraseñas de las bases, las claves de
firma y los AppRoles, y cada servicio los lee al arrancar. El primer usuario es
`superadmin@clinica.local` y su enlace de activación llega a Mailpit.

Para usar un modelo real en lugar del simulador, carga en LM Studio un modelo con tool calling (por
ejemplo `qwen3-8b`) y define `AI_ASSISTANT_LLM_BASE_URL=http://host.docker.internal:1234`.

| Qué | Dónde |
|---|---|
| API (gateway) | http://localhost:8080 |
| Mailpit (correos de activación) | http://127.0.0.1:8025 |
| Kafka UI | http://127.0.0.1:8090 |
| Eureka (con la credencial de `secret/eureka/client`) | http://127.0.0.1:8761 |
| Swagger de cada servicio | `http://127.0.0.1:<puerto>/swagger-ui.html`, con los puertos de `docker-compose.debug.yml` |

Sin `docker-compose.debug.yml` solo queda publicado el gateway.

---

## Pruebas

```bash
cd BackEnd-Clinica/billing-service
mvn verify        # unitarias + integración con Testcontainers (necesita Docker)
```

Cada servicio tiene pruebas unitarias, pruebas de integración contra bases, Kafka y OpenBao reales en
contenedores, y reglas de arquitectura con ArchUnit. Sobre el stack levantado:

```bash
cd BackEnd-Clinica
sh platform/e2e/auth-e2e.sh          # login, TOTP, sesiones y revocación
sh platform/e2e/clinical-e2e.sh      # pacientes e historia clínica
sh platform/e2e/admissions-e2e.sh    # episodios, camas y comprobantes
sh platform/e2e/billing-e2e.sh       # de la venta a la glosa, con DIAN y MUV simulados
sh platform/e2e/assistant-e2e.sh     # asistente de facturas con el modelo simulado
sh platform/e2e/openbao-e2e.sh       # conmutación del clúster y aislamiento de secretos
```

La CI (`.github/workflows/backend-ci.yml`) prueba las librerías y cada servicio en cada push, y corre la
suite E2E completa cada noche o a mano. Las pruebas de carga con k6 están en la carpeta `load-test` de
los servicios que las tienen (auth, patient, clinical-history, contracting, practitioners, admissions y
billing).

---

## Estructura

```
Clinica/
├── BackEnd-Clinica/
│   ├── api-gateway/  auth-service/  patient-service/  clinical-history-service/
│   ├── contracting-service/  practitioners-service/  admissions-service/
│   ├── billing-service/  ai-assistant-service/  eureka-service/
│   ├── libs/                  # librerías compartidas con versión fija
│   ├── platform/
│   │   ├── openbao/           # políticas, agente y aprovisionamiento del clúster
│   │   ├── kafka/  kafka-connect/
│   │   ├── simulators/        # WireMock de la DIAN, el MUV y el modelo local
│   │   └── e2e/               # pruebas de extremo a extremo
│   ├── docker-compose.yml
│   └── docker-compose.debug.yml
├── FrontEnd-Clinica/          # Astro; por ahora solo la base del proyecto
└── docs/                      # arquitectura, seguridad, API y variables de entorno
```

Cada servicio documenta sus reglas de negocio en su carpeta `docs/`, y los que publican eventos dejan sus
contratos en `events/`.

---

## Licencia

[MIT](LICENSE) © Ymid Ortega · [GitHub](https://github.com/YmidOrtega) · [LinkedIn](https://linkedin.com/in/ymidortega)
