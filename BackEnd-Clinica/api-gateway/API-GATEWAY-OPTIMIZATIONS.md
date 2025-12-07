# API Gateway - Optimizaciones Implementadas

Este documento describe las optimizaciones transversales implementadas en el API Gateway siguiendo las mejores prácticas de seguridad y código limpio.

## 📋 Tabla de Contenidos

1. [Rate Limiting](#rate-limiting)
2. [Circuit Breaker](#circuit-breaker)
3. [Request Logging](#request-logging)
4. [Métricas de Latencia](#métricas-de-latencia)
5. [CORS](#cors)
6. [Configuración](#configuración)

---

## 🚦 Rate Limiting

### Implementación

-  **Tecnología**: Redis + Bucket4j
-  **Límites configurados**:
   -  **100 requests/minuto** por usuario autenticado
   -  **1000 requests/minuto** por dirección IP

### Características

-  ✅ Almacenamiento distribuido con Redis para escalabilidad
-  ✅ Buckets independientes por usuario e IP
-  ✅ Headers informativos en respuestas:
   -  `X-RateLimit-Remaining`: Tokens restantes
   -  `Retry-After`: Segundos hasta el próximo reinicio
-  ✅ Respuesta HTTP 429 (Too Many Requests) cuando se excede el límite

### Archivos

-  `RateLimitService.java`: Lógica del rate limiting
-  `RateLimitFilter.java`: Filtro global que aplica los límites

### Ejemplo de uso

```bash
# Respuesta cuando se excede el límite
HTTP/1.1 429 Too Many Requests
X-RateLimit-Remaining: 0
Retry-After: 60

{
  "error": "Límite de peticiones por usuario excedido (máx: 100/min)",
  "status": 429,
  "timestamp": "2024-12-06T...",
  "remainingTokens": 0
}
```

---

## 🔄 Circuit Breaker

### Implementación

-  **Tecnología**: Resilience4j
-  **Configuración refinada por servicio**

### Parámetros Globales

-  ⚡ **3 fallos consecutivos** → Circuito abierto
-  ⏱️ **30 segundos** en estado abierto antes de half-open
-  🔄 **Retry con backoff exponencial** (multiplicador: 2x)
   -  Intento 1: espera 1s
   -  Intento 2: espera 2s
   -  Intento 3: espera 4s

### Configuraciones Específicas

#### Auth Service

-  Mayor tolerancia debido a criticidad
-  Timeout: 8 segundos
-  5 fallos mínimos antes de abrir
-  45 segundos en estado abierto

#### AI Assistant Service

-  Timeout extendido: 15 segundos
-  Ideal para operaciones de IA que toman más tiempo

#### Data Services (Patient, Billing, etc.)

-  Configuración estándar
-  Timeout: 5 segundos

### Archivos

-  `CircuitBreakerConfiguration.java`: Configuración de circuit breakers

### Monitoreo

Endpoints actuator disponibles:

-  `/actuator/circuitbreakers`
-  `/actuator/circuitbreakerevents`

---

## 📝 Request Logging

### Implementación

-  **Base de datos**: PostgreSQL
-  **Procesamiento**: Asíncrono (no bloquea respuestas)

### Información Capturada

-  ✅ User ID y Email
-  ✅ Endpoint y método HTTP
-  ✅ Status code de respuesta
-  ✅ Timestamp
-  ✅ Duración en milisegundos
-  ✅ IP del cliente (considerando proxies)
-  ✅ User-Agent
-  ✅ Nombre del servicio
-  ✅ Mensaje de error (si aplica)

### Características

-  🔍 Índices optimizados para queries analíticas
-  📊 Vistas SQL para analytics:
   -  `v_service_analytics`: Estadísticas por servicio
   -  `v_top_users`: Usuarios más activos
   -  `v_top_endpoints`: Endpoints más utilizados
-  🧹 Función de limpieza automática (logs > 90 días)

### Archivos

-  `RequestLog.java`: Entidad JPA
-  `RequestLogRepository.java`: Repositorio con queries analíticas
-  `RequestLogService.java`: Servicio de logging asíncrono
-  `RequestLoggingFilter.java`: Filtro global de logging
-  `db-init.sql`: Scripts de inicialización de BD

### Queries Analíticas Disponibles

```java
// Logs de un usuario en rango de fechas
findByUserIdAndTimestampBetween(userId, start, end)

// Estadísticas de endpoints más usados
getEndpointStatistics(start, end)

// Estadísticas de errores por código
getErrorStatistics(start, end)

// Latencia promedio por servicio
getAverageLatencyByService(start, end)
```

---

## 📊 Métricas de Latencia

### Implementación

-  **Tecnología**: Micrometer + Prometheus
-  **Percentiles**: p50, p90, p99

### Métricas Capturadas

#### Por Endpoint

```
gateway.request.duration{
  endpoint="/api/v1/patients",
  method="GET",
  service="patients",
  status="200"
}
```

#### Contadores

-  `gateway.requests.total`: Total de peticiones
-  `gateway.requests.errors`: Peticiones con error (4xx, 5xx)

### Etiquetas (Tags)

-  `endpoint`: Path completo
-  `method`: Método HTTP
-  `service`: Nombre del servicio destino
-  `status`: Código de respuesta HTTP
-  `application`: api-gateway
-  `environment`: dev/prod

### Archivos

-  `MetricsConfiguration.java`: Configuración de métricas
-  `RequestLoggingFilter.java`: Captura de métricas integrada

### Endpoints Disponibles

-  `/actuator/metrics`: Lista de métricas disponibles
-  `/actuator/prometheus`: Formato Prometheus
-  `/actuator/metrics/gateway.request.duration`: Métricas de latencia

### Ejemplo de Consulta Prometheus

```promql
# Latencia p99 por servicio en los últimos 5 minutos
histogram_quantile(0.99,
  rate(gateway_request_duration_bucket[5m])
) by (service)

# Requests por segundo por endpoint
rate(gateway_requests_total[1m]) by (endpoint, status)

# Tasa de error por servicio
rate(gateway_requests_errors[5m]) by (service)
```

---

## 🌐 CORS

### Implementación

Configuración segura siguiendo mejores prácticas:

### Características de Seguridad

-  ✅ **Orígenes específicos** (NO wildcard `*`)
-  ✅ **Credentials permitidos** de forma segura
-  ✅ **Headers controlados** (lista blanca)
-  ✅ **Métodos limitados** a los necesarios
-  ✅ **Cache de preflight** (1 hora)

### Configuración por Defecto

```yaml
cors:
   allowed-origins:
      - http://localhost:3000
      - http://localhost:4321
      - http://localhost:4200
   allowed-methods: GET,POST,PUT,DELETE,PATCH,OPTIONS
   max-age: 3600
```

### Headers Permitidos

-  Authorization
-  Content-Type
-  Accept
-  Origin
-  X-Requested-With

### Headers Expuestos

-  Authorization
-  X-Total-Count
-  X-RateLimit-Remaining
-  Retry-After
-  Content-Disposition

### Archivos

-  `CorsConfiguration.java`: Configuración CORS

### Configuración para Producción

```yaml
# application-prod.yml
cors:
   allowed-origins: https://mi-app.com,https://www.mi-app.com
   max-age: 86400 # 24 horas
```

---

## ⚙️ Configuración

### Variables de Entorno

#### Base de Datos

```bash
DB_USERNAME=postgres
DB_PASSWORD=your_password
```

#### Redis

```bash
REDIS_HOST=localhost
REDIS_PORT=6379
REDIS_PASSWORD=
```

#### CORS

```bash
CORS_ORIGINS=http://localhost:3000,http://localhost:4321
```

### Configuración de application.yml

Todas las configuraciones están centralizadas en `application.yml`:

```yaml
# Rate Limiting (Redis)
spring.data.redis.*

# Request Logging (PostgreSQL)
spring.datasource.*
spring.jpa.*

# Circuit Breaker
resilience4j.circuitbreaker.*
resilience4j.retry.*

# Métricas
management.endpoints.*
management.metrics.*

# CORS
cors.*
```

---

## 🚀 Inicio Rápido

### 1. Instalar Dependencias

```bash
cd BackEnd-Clinica/api-gateway
mvn clean install
```

### 2. Levantar Infraestructura

```bash
# PostgreSQL
docker run -d --name postgres-gateway \
  -e POSTGRES_DB=clinica_gateway \
  -e POSTGRES_USER=postgres \
  -e POSTGRES_PASSWORD=postgres \
  -p 5432:5432 postgres:15

# Redis
docker run -d --name redis-gateway \
  -p 6379:6379 redis:7-alpine

# Inicializar BD (opcional - JPA lo hace automáticamente)
psql -h localhost -U postgres -d clinica_gateway -f src/main/resources/db-init.sql
```

### 3. Ejecutar API Gateway

```bash
mvn spring-boot:run
```

### 4. Verificar Endpoints

-  Health: http://localhost:8080/actuator/health
-  Métricas: http://localhost:8080/actuator/metrics
-  Prometheus: http://localhost:8080/actuator/prometheus
-  Circuit Breakers: http://localhost:8080/actuator/circuitbreakers

---

## 📈 Monitoreo y Observabilidad

### Grafana Dashboard (Recomendado)

Importar métricas de Prometheus para visualizar:

-  Latencia por percentiles (p50, p90, p99)
-  Tasa de peticiones por servicio
-  Tasa de errores
-  Estado de circuit breakers
-  Rate limiting por usuario/IP

### Logs

Los logs se guardan en:

-  **Consola**: Logs de aplicación
-  **Base de datos**: Tabla `request_logs` para analytics

### Queries Útiles

```sql
-- Peticiones en la última hora
SELECT COUNT(*) FROM request_logs
WHERE timestamp > NOW() - INTERVAL '1 hour';

-- Top 10 endpoints más lentos
SELECT endpoint, AVG(duration_ms) as avg_ms
FROM request_logs
GROUP BY endpoint
ORDER BY avg_ms DESC
LIMIT 10;

-- Tasa de errores por servicio
SELECT service_name,
       COUNT(*) FILTER (WHERE status_code >= 400) * 100.0 / COUNT(*) as error_rate
FROM request_logs
WHERE timestamp > NOW() - INTERVAL '1 day'
GROUP BY service_name;
```

---

## 🔒 Seguridad

### Buenas Prácticas Implementadas

1. **Rate Limiting**: Previene ataques de fuerza bruta y DDoS
2. **CORS Restrictivo**: Solo orígenes permitidos explícitamente
3. **Headers de Seguridad**: Expone solo información necesaria
4. **Logging Completo**: Auditoría de todas las peticiones
5. **IP Tracking**: Considera proxies (X-Forwarded-For)
6. **Async Logging**: No impacta rendimiento
7. **Retry con Backoff**: Evita sobrecarga en servicios degradados
8. **Circuit Breaker**: Protege servicios de cascadas de fallos

---

## 🧪 Testing

### Probar Rate Limiting

```bash
# Hacer 101 peticiones rápidas (debería fallar la #101)
for i in {1..101}; do
  curl -H "Authorization: Bearer YOUR_TOKEN" \
       http://localhost:8080/api/v1/patients
done
```

### Probar Circuit Breaker

```bash
# Detener un servicio y hacer peticiones
# El circuit breaker se abrirá después de 3 fallos
curl http://localhost:8080/api/v1/service-down

# Verificar estado
curl http://localhost:8080/actuator/circuitbreakers
```

### Verificar Métricas

```bash
# Ver todas las métricas
curl http://localhost:8080/actuator/metrics

# Ver métricas específicas de latencia
curl http://localhost:8080/actuator/metrics/gateway.request.duration
```

---

## 📚 Referencias

-  [Resilience4j Documentation](https://resilience4j.readme.io/)
-  [Bucket4j Documentation](https://bucket4j.com/)
-  [Micrometer Documentation](https://micrometer.io/docs)
-  [Spring Cloud Gateway](https://spring.io/projects/spring-cloud-gateway)
-  [CORS Best Practices](https://developer.mozilla.org/en-US/docs/Web/HTTP/CORS)

---

## 👨‍💻 Arquitectura de Filtros

Orden de ejecución de filtros (menor a mayor):

1. **AuthenticationFilter** (Order: -1) - Autenticación JWT
2. **RateLimitFilter** (Order: 0) - Rate limiting
3. **RequestLoggingFilter** (Order: 1) - Logging y métricas

Este orden garantiza que:

-  Primero se autentica al usuario
-  Luego se aplican límites de rate
-  Finalmente se registran todas las peticiones con contexto completo

---

¡Implementación completa! 🎉
