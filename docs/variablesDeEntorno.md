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

#### Patient Service
```bash
PATIENT_DB_ROOT_PASSWORD=SecureRootPass2024!
PATIENT_DB_NAME=patient_db
PATIENT_DB_USER=patient_user
PATIENT_DB_PASSWORD=PatientSecure123!
PATIENT_DB_HOST=jdbc:mysql://localhost:3307/patient_db?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true
```

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
AUTH_DB_ROOT_PASSWORD=AuthRootPass2024!
AUTH_DB_NAME=auth_db
AUTH_DB_USER=auth_user
AUTH_DB_PASSWORD=AuthSecure123!
AUTH_DB_HOST=jdbc:mysql://localhost:3312/auth_db?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true
```

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
JWT_PUBLIC_KEY=MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA...
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
GEMINI_API_KEY=AIzaSyDjD6m2BHT3qcPSJq3pLzUM569VGJembOY
GEMINI_MODEL_NAME=gemini-1.5-pro
```

---

## Security Configuration

### Pattern
```
AUTH_{FEATURE}_{PROPERTY}
```

### Account Lockout

```bash
AUTH_MAX_LOGIN_ATTEMPTS=5
AUTH_LOCKOUT_DURATION_MINUTES=30
```

### Password Policy

```bash
AUTH_PASSWORD_MIN_LENGTH=8
AUTH_PASSWORD_REQUIRE_UPPERCASE=true
AUTH_PASSWORD_REQUIRE_LOWERCASE=true
AUTH_PASSWORD_REQUIRE_DIGIT=true
AUTH_PASSWORD_REQUIRE_SPECIAL_CHAR=true
AUTH_PASSWORD_HISTORY_COUNT=5
AUTH_PASSWORD_EXPIRATION_DAYS=90
```

### Cleanup Jobs

```bash
AUTH_CLEANUP_ENABLED=true
AUTH_CLEANUP_CRON=0 0 2 * * *
```

---

## API Gateway Configuration

### Pattern
```
GATEWAY_{COMPONENT}_{PROPERTY}
```

### Examples

```bash
GATEWAY_DB_HOST=localhost:5432
GATEWAY_DB_NAME=clinica_gateway
GATEWAY_RATE_LIMIT_REQUESTS=100
GATEWAY_RATE_LIMIT_DURATION=60
GATEWAY_CORS_ORIGINS=http://localhost:3000,http://localhost:4321
```

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
