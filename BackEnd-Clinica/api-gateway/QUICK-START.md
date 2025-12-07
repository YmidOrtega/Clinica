# 🚀 Quick Start Guide - API Gateway Optimizations

## Resumen de Optimizaciones Implementadas

✅ **Rate Limiting**: 100 req/min por usuario, 1000 req/min por IP  
✅ **Circuit Breaker**: 3 fallos → circuito abierto, retry con backoff exponencial  
✅ **Request Logging**: Todas las peticiones guardadas en PostgreSQL  
✅ **Métricas de Latencia**: Percentiles p50, p90, p99 por endpoint y servicio  
✅ **CORS**: Configuración segura para frontend

---

## 📦 Requisitos Previos

-  Java 21
-  Maven 3.8+
-  Docker & Docker Compose
-  PostgreSQL 15+ (o usar Docker)
-  Redis 7+ (o usar Docker)

---

## 🐳 Opción 1: Usando Docker Compose (Recomendado)

### 1. Configurar variables de entorno

```bash
cd BackEnd-Clinica
cp .env.example .env
# Editar .env si es necesario
```

### 2. Levantar infraestructura

```bash
# Levantar solo las bases de datos y Redis
docker-compose up -d gateway-db redis

# Verificar que estén corriendo
docker-compose ps
```

### 3. Compilar y ejecutar el API Gateway

```bash
cd api-gateway
mvn clean install
mvn spring-boot:run
```

---

## 💻 Opción 2: Instalación Local

### 1. Instalar PostgreSQL

```bash
# Ubuntu/Debian
sudo apt-get install postgresql-15

# macOS
brew install postgresql@15

# Crear base de datos
psql -U postgres
CREATE DATABASE clinica_gateway;
\q
```

### 2. Instalar Redis

```bash
# Ubuntu/Debian
sudo apt-get install redis-server

# macOS
brew install redis

# Iniciar Redis
redis-server
```

### 3. Configurar application.yml

```bash
cd api-gateway/src/main/resources
# Editar application.yml con tus credenciales locales
```

### 4. Ejecutar API Gateway

```bash
cd api-gateway
mvn spring-boot:run
```

---

## ✅ Verificación

### 1. Health Check

```bash
curl http://localhost:8080/actuator/health
```

Respuesta esperada:

```json
{
  "status": "UP",
  "components": {
    "db": {"status": "UP"},
    "redis": {"status": "UP"},
    ...
  }
}
```

### 2. Verificar Métricas

```bash
# Lista de métricas
curl http://localhost:8080/actuator/metrics

# Métricas de latencia
curl http://localhost:8080/actuator/metrics/gateway.request.duration

# Formato Prometheus
curl http://localhost:8080/actuator/prometheus
```

### 3. Probar Rate Limiting

```bash
# Script para hacer 101 peticiones
for i in {1..101}; do
  echo "Request $i"
  curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8080/actuator/health
  sleep 0.1
done
```

La petición #101 debería devolver `429 Too Many Requests`.

### 4. Verificar Circuit Breaker

```bash
curl http://localhost:8080/actuator/circuitbreakers
```

### 5. Verificar Request Logs en BD

```bash
# Conectar a PostgreSQL
docker exec -it gateway-db psql -U postgres -d clinica_gateway

# Ver logs recientes
SELECT user_id, endpoint, status_code, duration_ms, timestamp
FROM request_logs
ORDER BY timestamp DESC
LIMIT 10;

# Estadísticas por servicio
SELECT * FROM v_service_analytics
WHERE date = CURRENT_DATE;

# Top endpoints
SELECT * FROM v_top_endpoints LIMIT 10;
```

---

## 📊 Monitoreo con Prometheus + Grafana (Opcional)

### 1. Crear docker-compose-monitoring.yml

```yaml
version: "3.8"

services:
   prometheus:
      image: prom/prometheus:latest
      container_name: prometheus
      ports:
         - "9090:9090"
      volumes:
         - ./prometheus.yml:/etc/prometheus/prometheus.yml
         - prometheus_data:/prometheus
      command:
         - "--config.file=/etc/prometheus/prometheus.yml"
      networks:
         - clinica-net

   grafana:
      image: grafana/grafana:latest
      container_name: grafana
      ports:
         - "3001:3000"
      environment:
         - GF_SECURITY_ADMIN_PASSWORD=admin
      volumes:
         - grafana_data:/var/lib/grafana
      networks:
         - clinica-net

volumes:
   prometheus_data:
   grafana_data:

networks:
   clinica-net:
      external: true
```

### 2. Crear prometheus.yml

```yaml
global:
   scrape_interval: 15s

scrape_configs:
   - job_name: "api-gateway"
     metrics_path: "/actuator/prometheus"
     static_configs:
        - targets: ["host.docker.internal:8080"]
```

### 3. Levantar monitoring

```bash
docker-compose -f docker-compose-monitoring.yml up -d
```

### 4. Acceder a Grafana

-  URL: http://localhost:3001
-  User: admin
-  Pass: admin

### 5. Configurar Dashboard

1. Add Data Source → Prometheus → http://prometheus:9090
2. Import Dashboard → ID: 4701 (JVM Micrometer)
3. Crear queries personalizadas:

```promql
# Latencia p99 por servicio
histogram_quantile(0.99, rate(gateway_request_duration_bucket[5m])) by (service)

# Requests por segundo
rate(gateway_requests_total[1m])

# Tasa de error
rate(gateway_requests_errors[5m]) / rate(gateway_requests_total[5m])
```

---

## 🔧 Configuración para Producción

### 1. Variables de entorno

```bash
# application-prod.yml
export SPRING_PROFILES_ACTIVE=prod
export DB_USERNAME=prod_user
export DB_PASSWORD=secure_password
export REDIS_HOST=redis-prod.example.com
export REDIS_PASSWORD=redis_secure_password
export CORS_ORIGINS=https://app.example.com
```

### 2. Límites de Rate Limiting (ajustar si es necesario)

Editar `RateLimitService.java`:

```java
private static final long USER_RATE_LIMIT = 200;  // Aumentar para prod
private static final long IP_RATE_LIMIT = 2000;   // Aumentar para prod
```

### 3. Retención de logs

Ejecutar periódicamente:

```sql
-- Mantener solo últimos 90 días
SELECT cleanup_old_logs();
```

O configurar un cron job:

```bash
# crontab -e
0 2 * * * psql -h localhost -U postgres -d clinica_gateway -c "SELECT cleanup_old_logs();"
```

---

## 🐛 Troubleshooting

### Error: "Cannot connect to database"

```bash
# Verificar que PostgreSQL esté corriendo
docker-compose ps gateway-db

# Ver logs
docker-compose logs gateway-db

# Reiniciar
docker-compose restart gateway-db
```

### Error: "Cannot connect to Redis"

```bash
# Verificar Redis
docker-compose ps redis

# Probar conexión
redis-cli ping

# Ver logs
docker-compose logs redis
```

### Rate Limiting no funciona

```bash
# Verificar conexión a Redis
redis-cli
> KEYS rate_limit:*
> GET rate_limit:user:some-user-id
```

### Métricas no aparecen

```bash
# Verificar endpoint de métricas
curl http://localhost:8080/actuator/metrics

# Verificar que Micrometer esté configurado
curl http://localhost:8080/actuator/prometheus | grep gateway_request
```

### Circuit Breaker no se abre

```bash
# Verificar configuración
curl http://localhost:8080/actuator/circuitbreakers

# Ver eventos
curl http://localhost:8080/actuator/circuitbreakerevents
```

---

## 📚 Recursos Adicionales

-  [Documentación completa](./API-GATEWAY-OPTIMIZATIONS.md)
-  [Script de inicialización de BD](./src/main/resources/db-init.sql)
-  [Colección Postman](./Postman-Collection-API-Gateway.json) _(crear si es necesario)_

---

## 🧪 Tests de Carga (Opcional)

### Usar Apache Bench

```bash
# 1000 peticiones, 10 concurrentes
ab -n 1000 -c 10 http://localhost:8080/actuator/health
```

### Usar JMeter

1. Descargar [Apache JMeter](https://jmeter.apache.org/)
2. Crear Thread Group con 100 usuarios
3. Agregar HTTP Request al endpoint del gateway
4. Ejecutar y ver métricas en Grafana

---

## 📞 Soporte

Si encuentras algún problema:

1. Revisa los logs: `docker-compose logs api-gateway`
2. Verifica el health: `curl http://localhost:8080/actuator/health`
3. Consulta la documentación: [API-GATEWAY-OPTIMIZATIONS.md](./API-GATEWAY-OPTIMIZATIONS.md)

---

¡Todo listo! 🎉

El API Gateway ahora está optimizado con:

-  Rate Limiting distribuido con Redis
-  Circuit Breaker avanzado con retry exponencial
-  Logging completo de peticiones en PostgreSQL
-  Métricas de latencia con percentiles
-  CORS configurado de forma segura

Para más detalles, consulta [API-GATEWAY-OPTIMIZATIONS.md](./API-GATEWAY-OPTIMIZATIONS.md)
