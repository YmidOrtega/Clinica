# Variables de entorno — Clínica

## Principio

**No hace falta un `.env` para levantar el proyecto.** Los secretos (contraseñas de bases, claves de
firma, códigos TOTP, credenciales de Eureka y del Ministerio) no son variables de entorno: viven en
OpenBao, `openbao-init` los genera la primera vez y cada servicio los lee al arrancar con el perfil
`openbao`. Las variables que quedan son las que cambian el comportamiento o el entorno (URLs, tiempos,
réplicas), y todas tienen un valor por defecto que sirve para el stack local.

Los servicios aceptan las variables de usuario y contraseña de su base (`<SERVICIO>_DB_APP_USER`,
`_APP_PASSWORD`, `_MIGRATOR_USER`, `_MIGRATOR_PASSWORD`) solo para correr fuera de Docker o en pruebas,
sin el perfil `openbao`.

---

## 1. Secretos en OpenBao

Rutas del motor KV (`secret/`) que siembra `platform/openbao/scripts/configure.sh`:

| Ruta | Claves | Quién la lee |
|---|---|---|
| `<servicio>/db/root` | `password` | la base del servicio, vía `openbao-agent` |
| `<servicio>/db/migrator` | `username`, `password` | el servicio (Flyway) y su base |
| `<servicio>/db/app` | `username`, `password` | el servicio y su base |
| `<servicio>/db/debezium` | `username`, `password` | la base y `kafka-connect` |
| `auth/bootstrap` | `super-admin-email`, `super-admin-name` | `auth-service`, para invitar al primer `SUPER_ADMIN` |
| `gateway/redis` | `password` | `api-gateway` y `gateway-redis` |
| `clinical/storage/root` · `clinical/storage/attachments` | usuario raíz · `access-key`, `secret-key` | MinIO · `clinical-history-service` |
| `billing/dian/software` | `software-id`, `software-pin`, `test-set-id` | `billing-service` |
| `billing/ministry/credentials` | `document-type`, `document-number`, `password` | `billing-service` (login SISPRO del MUV) |
| `eureka/client` | `username`, `password` | los 9 servicios y `eureka-service` |

`<servicio>` es `patient`, `clinical`, `auth`, `contracting`, `practitioners`, `admissions`, `billing` y
`assistant` (este último sin usuario Debezium).

Claves del motor transit (no exportables): `auth-jwt` (firma de tokens), `<servicio>-client` (aserciones
de cada cliente OAuth), `clinical-kek` y `clinical-seal` (cifrado y sello de la historia clínica),
`admissions-seal` y `billing-seal` (sello de comprobantes y representaciones gráficas) y `billing-dian`
(certificado de firma de la DIAN, importado). Operación y rotación en
[`BackEnd-Clinica/platform/openbao/README.md`](../BackEnd-Clinica/platform/openbao/README.md).

---

## 2. Variables del stack (`docker-compose.yml`)

| Variable | Por defecto | Qué hace |
|---|---|---|
| `<SERVICIO>_SERVICE_REPLICAS` | `2` | réplicas de patient, clinical, auth, contracting, practitioners, admissions y billing |
| `<SERVICIO>_DB_NAME` | `<servicio>_db` | nombre de la base de cada servicio |
| `AUTH_ISSUER` | `http://localhost:8080/auth` | emisor de los tokens; debe ser la URL pública de auth a través del gateway |
| `GATEWAY_FRONTEND_ORIGINS` · `GATEWAY_FRONTEND_HOME_URL` | `http://localhost:4321` · `http://localhost:4321/` | orígenes del frontend aceptados por CORS y página de inicio |
| `GATEWAY_SESSION_SAME_SITE` · `GATEWAY_SESSION_COOKIE_SECURE` | `lax` · `false` | cookie de sesión del gateway; `true` detrás de HTTPS |
| `AUTH_SESSION_COOKIE_SECURE` | `false` | cookie de sesión de auth; `true` detrás de HTTPS |
| `AUTH_LOGIN_URL`, `AUTH_HOME_URL`, `AUTH_ACTIVATION_URL`, `AUTH_PASSWORD_RESET_URL` | pantallas en `http://localhost:4321` | a dónde llevan los enlaces y redirecciones de auth |
| `AUTH_GATEWAY_REDIRECT_URI` · `AUTH_GATEWAY_POST_LOGOUT_URI` | `http://localhost:8080/login/oauth2/code/clinica` · `http://localhost:4321/` | URIs registradas del cliente OAuth del gateway |
| `AI_ASSISTANT_LLM_BASE_URL` | `http://host.docker.internal:1234` (con debug, `http://llm-simulator:8080`) | API compatible con OpenAI del modelo local |
| `AI_ASSISTANT_LLM_MODEL` · `AI_ASSISTANT_LLM_TIMEOUT` | `qwen3-8b` · `80s` | modelo cargado en LM Studio y espera máxima |
| `STEP_UP_MAX_AGE_SECONDS` | `300` | solo en debug: antigüedad máxima del segundo factor para el step-up (los E2E la bajan) |
| `KAFKA_CLUSTER_ID` | fijo | identificador del clúster KRaft |

---

## 3. Variables comunes a los servicios

| Variable | Por defecto | Qué hace |
|---|---|---|
| `<SERVICIO>_SERVICE_PORT` | el puerto del servicio | puerto HTTP |
| `<SERVICIO>_DB_URL` · `<SERVICIO>_DB_POOL_SIZE` | base local · `15` | URL JDBC y tamaño del pool |
| `AUTH_JWKS_URI` · `AUTH_TOKEN_URI` | `http://localhost:8086/oauth2/…` | claves públicas y endpoint de tokens de auth |
| `<SERVICIO>_CLIENT_TRANSIT_KEY` | `<servicio>-client` | clave de transit con que el servicio firma su aserción de cliente |
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` o vacío | broker; vacío desactiva la revocación por `auth.users.v1` en los servicios que solo la usan para eso |
| `EUREKA_URL` · `EUREKA_PREFER_IP_ADDRESS` | `http://localhost:8761/eureka/` · `true` | registro; en compose lleva las credenciales de OpenBao y `false` |
| `OPENBAO_ADDR` | `https://openbao:8200` | dirección del clúster OpenBao (perfil `openbao`) |
| `TRACING_SAMPLING_PROBABILITY` | `0.1` | fracción de trazas muestreadas |
| `SWAGGER_UI_ENABLED` | `false` (`true` en debug) | publica `/swagger-ui.html` |

---

## 4. Variables propias de cada servicio

### api-gateway

| Variable | Por defecto | Qué hace |
|---|---|---|
| `GATEWAY_<SERVICIO>_SERVICE_URI` | `lb://<servicio>` (en compose, `http://<servicio>:<puerto>`) | destino de cada ruta |
| `AUTH_PUBLIC_URL` · `AUTH_INTERNAL_URL` | `http://localhost:8080/auth` · `http://localhost:8086` | auth visto por el navegador y desde el gateway |
| `GATEWAY_REDIS_HOST` · `GATEWAY_REDIS_PORT` | `localhost` · `6379` | Redis de sesiones |
| `GATEWAY_READ_TIMEOUT` · `GATEWAY_ASSISTANT_READ_TIMEOUT` | `30s` · `90s` | espera por la respuesta de un servicio; la del asistente es más larga |
| `GATEWAY_TRUSTED_PROXIES` | `.*` | proxies de confianza para resolver la IP del cliente |

### auth-service

| Variable | Por defecto | Qué hace |
|---|---|---|
| `AUTH_MAIL_HOST` · `AUTH_MAIL_PORT` · `AUTH_MAIL_FROM` | `localhost` · `1025` · `no-responder@clinica.local` | SMTP de los correos de activación y reseteo (Mailpit en local) |
| `AUTH_TOTP_ISSUER` | `Clinica` | nombre que muestra la app autenticadora |
| `AUTH_SIGNING_TRANSIT_KEY` | `auth-jwt` | clave de transit que firma los tokens |
| `AUTH_<CLIENTE>_CLIENT_KEY` | `<cliente>-client` | clave pública con que auth verifica cada cliente OAuth |
| `AUTH_BOOTSTRAP_SUPER_ADMIN_EMAIL` | vacía | correo del primer `SUPER_ADMIN`; con OpenBao sale de `secret/auth/bootstrap` |

### clinical-history-service

| Variable | Por defecto | Qué hace |
|---|---|---|
| `CLINICAL_ATTACHMENTS_ENDPOINT` · `_REGION` | vacía · `us-east-1` | almacenamiento S3 de anexos (MinIO en local) |
| `CLINICAL_ATTACHMENTS_STAGING_BUCKET` · `_ARCHIVE_BUCKET` | `clinical-attachments-staging` · `clinical-attachments` | bucket de espera y bucket WORM de archivo |
| `CLINICAL_ATTACHMENTS_CREATE_BUCKETS` | `false` | crea los buckets al arrancar (solo en pruebas) |
| `CLINICAL_ENCRYPTION_TRANSIT_KEY` · `CLINICAL_SEAL_TRANSIT_KEY` | `clinical-kek` · `clinical-seal` | clave maestra de cifrado y clave de sello |
| `CLINICAL_NOTES_EXTEMPORANEOUS_AFTER` | `24h` | a partir de cuándo una nota se registra como extemporánea |

### admissions-service

| Variable | Por defecto | Qué hace |
|---|---|---|
| `ADMISSIONS_PATIENT_EVENTS_ENABLED`, `_PRACTITIONER_EVENTS_ENABLED`, `_CLINICAL_EVENTS_ENABLED` | `true` | consumo de cada topic |
| `ADMISSIONS_COVERAGE_TTL` · `ADMISSIONS_COVERAGE_CACHE_SIZE` | `10m` · `5000` | caché de la verificación de cobertura |
| `ADMISSIONS_SEAL_TRANSIT_KEY` | `admissions-seal` | clave con que se sellan los comprobantes |
| `ADMISSIONS_PUBLIC_CHECKS_PER_WINDOW` · `_PUBLIC_CHECK_WINDOW` | `600` · `1m` | tope global de verificaciones públicas (el límite por cliente lo pone el gateway) |

### billing-service

| Variable | Por defecto | Qué hace |
|---|---|---|
| `BILLING_ADMISSION_EVENTS_ENABLED` · `BILLING_CLINICAL_EVENTS_ENABLED` | `true` | consumo de cada topic |
| `BILLING_PRIVATE_CONTRACT_UUID` | vacía | contrato con que se tasa la atención particular |
| `BILLING_DIAN_TEST_URL` · `BILLING_DIAN_PRODUCTION_URL` | servicios web de la DIAN | habilitación y producción (en debug, el simulador) |
| `BILLING_DIAN_SIGNING_KEY` | `billing-dian` | clave de transit del certificado de firma |
| `BILLING_DIAN_DELIVERY_DELAY` · `BILLING_DIAN_SIGNATURE_RETRY_DELAY` | `PT30S` · `PT1M` | envío a la DIAN y reintento de firmas pendientes |
| `BILLING_MINISTRY_VALIDATOR_URL` · `BILLING_MINISTRY_RETRY_DELAY` | `https://fevrips-api:9443` · `PT5M` | API FEV-RIPS del MUV y reintento de envíos pendientes |
| `BILLING_FILING_WARNING_BUSINESS_DAYS` · `BILLING_FILING_ALERTS_CRON` | `5` · `0 0 6 * * *` | aviso de radicación por vencer y hora de la tarea |
| `BILLING_OBJECTIONS_WARNING_BUSINESS_DAYS` · `BILLING_OBJECTIONS_ALERTS_CRON` | `3` · `0 5 6 * * *` | aviso de glosas por vencer y hora de la tarea |
| `BILLING_SEAL_TRANSIT_KEY` | `billing-seal` | sello de las representaciones gráficas |
| `BILLING_PUBLIC_CHECKS_PER_WINDOW` · `_PUBLIC_CHECK_WINDOW` | `600` · `1m` | tope global de verificaciones públicas |
| `BILLING_OUTBOX_RETENTION` | `7d` | cuánto se conservan los eventos ya publicados |

Las credenciales de la DIAN (`BILLING_DIAN_SOFTWARE_*`) y del Ministerio (`BILLING_MINISTRY_*`) salen de
OpenBao con el perfil `openbao`; las variables solo sirven fuera de Docker.

### ai-assistant-service

| Variable | Por defecto | Qué hace |
|---|---|---|
| `AI_ASSISTANT_LLM_BASE_URL` · `_MODEL` · `_API_KEY` · `_TIMEOUT` | `http://localhost:1234` · `qwen3-8b` · `lm-studio` · `80s` | modelo local compatible con OpenAI |
| `AI_ASSISTANT_INVOICE_EVENTS_ENABLED` | `true` | consumo de los topics de billing |
| `AI_ASSISTANT_REVIEW_DIAN_GRACE` · `_RIPS_GRACE` · `_SWEEP_DELAY` | `PT2H` · `PT24H` · `PT30M` | cuándo se marca una factura sin respuesta de la DIAN o sin CUV, y cada cuánto se revisa |
| `AI_ASSISTANT_ACTION_LIFETIME` | `PT30M` | vigencia de una acción propuesta |
| `AI_ASSISTANT_CONVERSATION_RETENTION` · `_PURGE_CRON` | `P30D` · `0 30 3 * * *` | cuánto se guarda una conversación sin actividad y cuándo se purga |

### eureka-service

| Variable | Por defecto | Qué hace |
|---|---|---|
| `EUREKA_USERNAME` · `EUREKA_PASSWORD` | `eureka` · vacía | credencial del registro; en compose sale de OpenBao y sin ella no arranca |
| `EUREKA_HOSTNAME` · `EUREKA_SELF_PRESERVATION` | `localhost` · `true` | nombre del servidor y autopreservación |

---

## 5. Convenciones

- Prefijo por servicio (`BILLING_`, `AI_ASSISTANT_`…) y nombres en mayúsculas con guion bajo.
- Duraciones en formato ISO 8601 (`PT30M`, `P30D`) o de Spring (`30s`, `10m`); crons de Spring con segundos.
- Todo valor por defecto debe servir para el stack local; lo que no tiene un valor seguro por defecto es un
  secreto y va a OpenBao.
