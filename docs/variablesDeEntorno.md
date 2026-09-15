# Environment Variables Documentation

## Overview

This document provides a comprehensive guide to all environment variables used in the Clinica microservices architecture. Following proper naming conventions ensures maintainability, clarity, and consistency across the system.

## Naming Conventions

### General Rules

1. **Use UPPERCASE with underscores**: `DATABASE_HOST`, `JWT_SECRET`
2. **Prefix by service/component**: `AUTH_`, `PATIENT_`, `BILLING_`
3. **Be descriptive and specific**: Avoid abbreviations unless commonly understood
4. **Group related variables**: Keep database configs together, API keys together, etc.

### Format Pattern

```
{SERVICE}_{COMPONENT}_{PROPERTY}
```

**Examples:**
- `AUTH_DB_HOST` - Auth service database host
- `PATIENT_DB_PASSWORD` - Patient service database password
- `JWT_ACCESS_TOKEN_EXPIRATION` - JWT access token expiration time

---

## Database Configuration

### Pattern
```
{SERVICE}_DB_{PROPERTY}
```

### Properties
- `ROOT_PASSWORD` - Root password for database
- `NAME` - Database name
- `USER` - Database user
- `PASSWORD` - User password
- `HOST` - Connection host (JDBC URL or hostname:port)

### Examples

#### Secretos en OpenBao

Las contraseñas y claves de `patient-service`, `clinical-history-service` y su infraestructura (MySQL,
Kafka Connect y almacenamiento S3) **ya no son variables de entorno**: viven en el clúster OpenBao y
`openbao-init` las genera la primera vez. Los servicios Spring las leen con el perfil `openbao`
(`SPRING_PROFILES_ACTIVE=openbao`), y MySQL, Kafka Connect y el almacenamiento las reciben como
archivos que renderiza `openbao-agent`. Rutas, políticas y operación en
[`BackEnd-Clinica/platform/openbao/README.md`](../BackEnd-Clinica/platform/openbao/README.md).

| Ruta KV (`secret/`)            | Claves                     | Quién la lee                                  |
| ------------------------------ | -------------------------- | --------------------------------------------- |
| `patient/db/root`              | `password`                 | `patient-db` (vía agente)                     |
| `patient/db/migrator`          | `username`, `password`     | `patient-service` (Flyway), `patient-db`      |
| `patient/db/app`               | `username`, `password`     | `patient-service`, `patient-db`               |
| `patient/db/debezium`          | `username`, `password`     | `patient-db`, `kafka-connect`                 |
| `clinical/db/*`                | igual que `patient/db/*`   | `clinical-history-service`, `clinical-db`, `kafka-connect` |
| `clinical/storage/root`        | `username`, `password`     | `clinical-storage`, `clinical-storage-init`   |
| `clinical/storage/attachments` | `access-key`, `secret-key` | `clinical-history-service`, `clinical-storage-init` |

`OPENBAO_ADDR` (por defecto `https://openbao:8200`) cambia la dirección del clúster. Fuera de Docker y
en los tests, sin el perfil `openbao`, los servicios siguen aceptando las variables
`PATIENT_DB_APP_USER`, `PATIENT_DB_APP_PASSWORD`, `PATIENT_DB_MIGRATOR_*` y sus equivalentes
`CLINICAL_*`.

#### Patient Service
```bash
PATIENT_DB_NAME=patient_db
PATIENT_DB_URL=jdbc:mysql://localhost:3307/patient_db
AUTH_ISSUER=http://localhost:8080/auth                  # emisor público de los tokens
AUTH_JWKS_URI=http://auth-service:8086/oauth2/jwks      # claves públicas ES256
KAFKA_BOOTSTRAP_SERVERS=kafka:9092                      # vacío desactiva la revocación por auth.users.v1
```

#### Clinical History Service
```bash
CLINICAL_DB_NAME=clinical_db
CLINICAL_DB_URL=jdbc:mysql://clinical-db:3306/clinical_db
AUTH_ISSUER=http://localhost:8080/auth
AUTH_JWKS_URI=http://auth-service:8086/oauth2/jwks
AUTH_TOKEN_URI=http://auth-service:8086/oauth2/token          # intercambio de tokens para llamar a patient-service
CLINICAL_CLIENT_TRANSIT_KEY=clinical-history-service-client   # opcional; firma de su aserción private_key_jwt

CLINICAL_TRANSIT_MOUNT=transit                                # opcional; motor transit de OpenBao
CLINICAL_ENCRYPTION_TRANSIT_KEY=clinical-kek                  # opcional; clave maestra aes256-gcm96
CLINICAL_SEAL_TRANSIT_KEY=clinical-seal                       # opcional; clave del sello ecdsa-p256

CLINICAL_ATTACHMENTS_ENDPOINT=http://clinical-storage:9000    # cualquier S3 con Object Lock
```

Las claves de sello y de cifrado **viven en el motor transit de OpenBao y nunca salen de él**; el
servicio no arranca sin OpenBao ni con el perfil `openbao` desactivado. `openbao-init` crea las claves.
Rotación, migración desde claves en archivos y recuperación en
`clinical-history-service/docs/claves-y-cifrado.md`.
Opcionales: `CLINICAL_SERVICE_PORT`, `CLINICAL_SERVICE_REPLICAS`, `CLINICAL_DB_POOL_SIZE`,
`EUREKA_URL`, `TRACING_SAMPLING_PROBABILITY`, `SWAGGER_UI_ENABLED`.

#### Plataforma de eventos
```bash
KAFKA_CLUSTER_ID=Q2xpbmljYUthZmthMDAwMQ   # opcional; identificador KRaft en Base64 de 16 bytes
PATIENT_SERVICE_REPLICAS=2               # opcional; instancias de patient-service
CLINICAL_SERVICE_REPLICAS=2              # opcional; instancias de clinical-history-service
```

El usuario `migrator` solo lo usa Flyway; la aplicación se conecta con el usuario `app`, que no tiene
permisos de `DELETE` ni de DDL sobre el registro, y Kafka Connect lee el outbox con el usuario
`debezium`. Los tokens se validan con el JWKS de `auth-service`; no hay claves de firma en variables de
entorno. Opcionales: `PATIENT_DB_POOL_SIZE`, `EUREKA_URL`, `TRACING_SAMPLING_PROBABILITY`,
`SWAGGER_UI_ENABLED`.

#### Billing Service
```bash
BILLING_DB_ROOT_PASSWORD=BillingRootPass2024!
BILLING_DB_NAME=billing_db
BILLING_DB_USER=billing_user
BILLING_DB_PASSWORD=BillingSecure123!
BILLING_DB_HOST=jdbc:mysql://localhost:3308/billing_db?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true
```

#### Admissions Service
```bash
ADMISSIONS_DB_ROOT_PASSWORD=AdmissionsRootPass2024!
ADMISSIONS_DB_NAME=admissions_db
ADMISSIONS_DB_USER=admissions_user
ADMISSIONS_DB_PASSWORD=AdmissionsSecure123!
ADMISSIONS_DB_HOST=localhost:3309
```

#### AI Assistant Service
```bash
AI_ASSISTANT_DB_ROOT_PASSWORD=AIRootPass2024!
AI_ASSISTANT_DB_NAME=ai_assistant_db
AI_ASSISTANT_DB_USER=ai_user
AI_ASSISTANT_DB_PASSWORD=AISecure123!
AI_ASSISTANT_DB_HOST=localhost:3310
```

#### Suppliers Service
```bash
SUPPLIERS_DB_ROOT_PASSWORD=SuppliersRootPass2024!
SUPPLIERS_DB_NAME=suppliers_db
SUPPLIERS_DB_USER=suppliers_user
SUPPLIERS_DB_PASSWORD=SuppliersSecure123!
SUPPLIERS_DB_HOST=jdbc:mysql://localhost:3311/suppliers_db?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true
```

#### Auth Service
```bash
AUTH_DB_NAME=auth_db                                        # opcional
AUTH_DB_URL=jdbc:mysql://auth-db:3306/auth_db
AUTH_SERVICE_REPLICAS=2                                     # opcional
AUTH_ISSUER=http://localhost:8080/auth                      # URL pública del emisor (a través del gateway)
AUTH_LOGIN_URL=http://localhost:4321/login                  # pantalla de login del frontend
AUTH_HOME_URL=http://localhost:4321/
AUTH_ACTIVATION_URL=http://localhost:4321/activar-cuenta    # el correo agrega ?token=
AUTH_PASSWORD_RESET_URL=http://localhost:4321/restablecer-contrasena
AUTH_GATEWAY_REDIRECT_URI=http://localhost:8080/login/oauth2/code/clinica
AUTH_GATEWAY_POST_LOGOUT_URI=http://localhost:4321/          # adónde vuelve el navegador tras cerrar sesión
AUTH_SESSION_COOKIE_SECURE=true                             # false solo en desarrollo sin HTTPS
AUTH_MAIL_HOST=mailpit
AUTH_MAIL_PORT=1025
AUTH_MAIL_FROM=no-responder@clinica.local                   # opcional
AUTH_SIGNING_TRANSIT_KEY=auth-jwt                           # opcional
```

El correo del primer `SUPER_ADMIN` sale de OpenBao (`secret/auth/bootstrap`: `super-admin-email`,
`super-admin-name`) o de `AUTH_BOOTSTRAP_SUPER_ADMIN_EMAIL`.

Las credenciales (`secret/auth/db/root`, `migrator`, `app`) están en OpenBao. Fuera del perfil
`openbao` se aceptan `AUTH_DB_APP_USER`, `AUTH_DB_APP_PASSWORD`, `AUTH_DB_MIGRATOR_USER` y
`AUTH_DB_MIGRATOR_PASSWORD`. Opcionales: `AUTH_SERVICE_PORT`, `AUTH_DB_POOL_SIZE`, `EUREKA_URL`,
`TRACING_SAMPLING_PROBABILITY`.

#### Clients Service
```bash
CLIENTS_DB_ROOT_PASSWORD=ClientsRootPass2024!
CLIENTS_DB_NAME=clients_db
CLIENTS_DB_USER=clients_user
CLIENTS_DB_PASSWORD=ClientsSecure123!
CLIENTS_DB_HOST=jdbc:mysql://localhost:3313/clients_db?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true
```

---

## JWT Configuration

### Pattern
```
JWT_{PROPERTY}
```

### Properties
- `SECRET` - Secret key for signing tokens
- `EXPIRATION` - Default token expiration (seconds)
- `PUBLIC_KEY` - Public key for token verification
- `ACCESS_TOKEN_EXPIRATION` - Access token expiration (seconds)
- `REFRESH_TOKEN_EXPIRATION` - Refresh token expiration (seconds)

### Examples

```bash
JWT_SECRET='MiClaveSecretaSuperSeguraParaJWT2024!@#$%^&*()'
JWT_EXPIRATION=3600
JWT_PUBLIC_KEY=TUClavePublic..
JWT_ACCESS_TOKEN_EXPIRATION=900        # 15 minutes
JWT_REFRESH_TOKEN_EXPIRATION=604800    # 7 days
```

---

## Redis Configuration

### Pattern
```
REDIS_{PROPERTY}
```

### Properties
- `HOST` - Redis server host
- `PORT` - Redis server port
- `PASSWORD` - Redis password
- `TIMEOUT` - Connection timeout

### Examples

```bash
REDIS_HOST=localhost
REDIS_PORT=6379
REDIS_PASSWORD=tu_password_redis_seguro
REDIS_TIMEOUT=2000
```

---

## External API Configuration

### Pattern
```
{SERVICE}_{API_NAME}_{PROPERTY}
```

### AI Assistant - Gemini API

```bash
AI_ASSISTANT_GEMINI_PROJECT_ID=gen-lang-client-0771416717
GEMINI_API_KEY=YOUR_GEMINI_API_KEY
GEMINI_MODEL_NAME=gemini-1.5-pro
```

---

## Security Configuration

La política de contraseñas y el frenado de intentos de `auth-service` se configuran con propiedades
(`clinica.auth.*` en `application.yml`); sus valores por defecto son los acordados y rara vez cambian:

| Propiedad                                         | Defecto        |
| ------------------------------------------------- | -------------- |
| `clinica.auth.password-deny-lists`                | `classpath:passwords/ncsc-100k-common.txt` |
| `clinica.auth.password-service-words`             | `clinica, clinicadeymid, ymid` |
| `clinica.auth.argon2.memory-kib` / `iterations` / `parallelism` | `19456` / `2` / `1` |
| `clinica.auth.login-throttle.address-free-attempts` / `address-max-delay` | `5` / `15m` |
| `clinica.auth.login-throttle.account-free-attempts` / `account-max-delay` | `10` / `1m` |
| `clinica.auth.login-throttle.account-lock-threshold` | `100`       |
| `clinica.auth.login-throttle.retention`           | `30d`          |

---

## API Gateway Configuration

### Pattern
```
GATEWAY_{COMPONENT}_{PROPERTY}
```

### Variables

```bash
GATEWAY_FRONTEND_ORIGINS=http://localhost:4321               # orígenes con CORS y returnTo permitidos, separados por coma
GATEWAY_FRONTEND_HOME_URL=http://localhost:4321/
GATEWAY_SESSION_SAME_SITE=lax                                # none si frontend y gateway están en dominios distintos
GATEWAY_SESSION_COOKIE_SECURE=true                           # false solo en desarrollo sin HTTPS
AUTH_PUBLIC_URL=http://localhost:8080/auth                   # emisor público; el navegador va aquí a autorizar
AUTH_INTERNAL_URL=http://auth-service:8086                   # token, JWKS y revocación desde el gateway
GATEWAY_REDIS_HOST=gateway-redis
GATEWAY_AUTH_SERVICE_URI=http://auth-service:8086             # destino de /auth/** y /api/v1/users; lb://auth-service con Eureka
GATEWAY_PATIENT_SERVICE_URI=http://patient-service:8081
GATEWAY_CLINICAL_SERVICE_URI=http://clinical-history-service:8089
GATEWAY_CLIENT_TRANSIT_KEY=api-gateway-client                # opcional
```

La contraseña de `gateway-redis` está en OpenBao (`secret/gateway/redis`); el gateway la lee con su
AppRole y el agente la renderiza para el contenedor de Redis. No hay base de datos del gateway.

---

## Service Discovery (Eureka)

### Pattern
```
EUREKA_{PROPERTY}
```

### Examples

```bash
EUREKA_SERVER_HOST=localhost
EUREKA_SERVER_PORT=8761
EUREKA_CLIENT_ENABLED=true
```

---

## Best Practices

### 1. **Never Commit Real Values**
- Use `.env.example` with placeholder values
- Add `.env` to `.gitignore`
- Document expected format, not actual secrets

### 2. **Use Strong Passwords**
```bash
# ❌ BAD
DB_PASSWORD=password123

# ✅ GOOD
DB_PASSWORD=K9$mP#nQ2@vL8xR!wT5
```

### 3. **Boolean Values**
```bash
# Use lowercase true/false
FEATURE_ENABLED=true
DEBUG_MODE=false
```

### 4. **Numeric Values**
```bash
# No quotes for numbers
MAX_CONNECTIONS=100
TIMEOUT_SECONDS=30
```

### 5. **URLs and Paths**
```bash
# Use full paths/URLs
API_BASE_URL=https://api.example.com/v1
FILE_UPLOAD_PATH=/var/uploads
```

### 6. **List of Values**
```bash
# Use comma-separated values
ALLOWED_ORIGINS=http://localhost:3000,http://localhost:4200,https://app.example.com
```

---

## Environment-Specific Variables

### Development
```bash
SPRING_PROFILES_ACTIVE=dev
LOG_LEVEL=DEBUG
ENABLE_SWAGGER=true
```

### Production
```bash
SPRING_PROFILES_ACTIVE=prod
LOG_LEVEL=WARN
ENABLE_SWAGGER=false
```

### Testing
```bash
SPRING_PROFILES_ACTIVE=test
LOG_LEVEL=INFO
USE_IN_MEMORY_DB=true
```

---

## Quick Reference Table

| Category | Prefix | Example |
|----------|--------|---------|
| Database | `{SERVICE}_DB_` | `PATIENT_DB_HOST` |
| JWT/Auth | `JWT_` or `AUTH_` | `JWT_SECRET`, `AUTH_MAX_LOGIN_ATTEMPTS` |
| Redis | `REDIS_` | `REDIS_HOST` |
| API Keys | `{API_NAME}_API_` | `GEMINI_API_KEY` |
| Gateway | `GATEWAY_` | `GATEWAY_RATE_LIMIT` |
| Service Discovery | `EUREKA_` | `EUREKA_SERVER_HOST` |

---

## Loading Environment Variables

### In Docker Compose
```yaml
services:
  patient-service:
    env_file:
      - .env
    environment:
      - SPRING_PROFILES_ACTIVE=prod
```

### In Spring Boot
```yaml
# application.yml
spring:
  datasource:
    url: ${PATIENT_DB_HOST}
    username: ${PATIENT_DB_USER}
    password: ${PATIENT_DB_PASSWORD}
```

### In Application Code
```java
@Value("${JWT_SECRET}")
private String jwtSecret;
```

---

## Troubleshooting

### Variable Not Found
1. Check `.env` file exists
2. Verify variable name matches exactly (case-sensitive)
3. Ensure no spaces around `=`
4. Check if docker-compose is loading the file

### Invalid Value Format
1. Remove quotes for numbers and booleans
2. Use quotes for strings with spaces
3. Escape special characters in passwords

### Connection Issues
1. Verify HOST includes protocol if needed (jdbc:mysql://)
2. Check port numbers are correct
3. Ensure network connectivity between services

---

## Security Checklist

- [ ] All production passwords are strong and unique
- [ ] `.env` file is in `.gitignore`
- [ ] API keys are rotated regularly
- [ ] Database credentials use principle of least privilege
- [ ] JWT secrets are at least 256 bits
- [ ] No sensitive data in logs
- [ ] Environment variables are documented
- [ ] Access to `.env` files is restricted

---

## Additional Resources

- [Spring Boot External Configuration](https://docs.spring.io/spring-boot/docs/current/reference/html/features.html#features.external-config)
- [Docker Environment Variables](https://docs.docker.com/compose/environment-variables/)
- [12 Factor App - Config](https://12factor.net/config)
