package com.ClinicaDeYmid.api_gateway.filter;

import com.ClinicaDeYmid.api_gateway.service.RequestLogService;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;

@Slf4j
@Component
@RequiredArgsConstructor
public class RequestLoggingFilter implements GlobalFilter, Ordered {

    private final RequestLogService requestLogService;
    private final MeterRegistry meterRegistry;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        Instant startTime = Instant.now();

        // Capturar información de la petición
        String endpoint = request.getURI().getPath();
        String httpMethod = request.getMethod().name();
        String userId = request.getHeaders().getFirst("X-User-ID");
        String userEmail = request.getHeaders().getFirst("X-User-Email");
        String ipAddress = getClientIp(request);
        String userAgent = request.getHeaders().getFirst("User-Agent");
        String serviceName = extractServiceName(endpoint);

        log.info("Request: {} {} | User: {} | IP: {}", httpMethod, endpoint,
                userId != null ? userId : "anonymous", ipAddress);

        return chain.filter(exchange)
                .doOnSuccess(aVoid -> {
                    // Calcular duración
                    long durationMs = Duration.between(startTime, Instant.now()).toMillis();
                    ServerHttpResponse response = exchange.getResponse();
                    Integer statusCode = response.getStatusCode() != null 
                            ? response.getStatusCode().value() 
                            : 200;

                    // Registrar métricas de latencia con Micrometer
                    recordMetrics(endpoint, httpMethod, serviceName, statusCode, durationMs);

                    // Guardar log en base de datos (async)
                    requestLogService.logRequest(
                            userId,
                            userEmail,
                            endpoint,
                            httpMethod,
                            statusCode,
                            durationMs,
                            ipAddress,
                            userAgent,
                            serviceName,
                            null
                    );

                    log.info("Response: {} {} | Status: {} | Duration: {}ms",
                            httpMethod, endpoint, statusCode, durationMs);
                })
                .doOnError(error -> {
                    // Manejar errores
                    long durationMs = Duration.between(startTime, Instant.now()).toMillis();
                    ServerHttpResponse response = exchange.getResponse();
                    Integer statusCode = response.getStatusCode() != null 
                            ? response.getStatusCode().value() 
                            : 500;

                    // Registrar métricas incluso en caso de error
                    recordMetrics(endpoint, httpMethod, serviceName, statusCode, durationMs);

                    // Guardar log de error
                    requestLogService.logRequest(
                            userId,
                            userEmail,
                            endpoint,
                            httpMethod,
                            statusCode,
                            durationMs,
                            ipAddress,
                            userAgent,
                            serviceName,
                            error.getMessage()
                    );

                    log.warn("Error: {} {} | Status: {} | Duration: {}ms | Error: {}",
                            httpMethod, endpoint, statusCode, durationMs, error.getMessage());
                });
    }

    /**
     * Registra métricas de latencia con percentiles usando Micrometer
     */
    private void recordMetrics(
            String endpoint,
            String httpMethod,
            String serviceName,
            Integer statusCode,
            long durationMs
    ) {
        try {
            // Timer con percentiles para latencia por endpoint
            Timer.builder("gateway.request.duration")
                    .tag("endpoint", endpoint)
                    .tag("method", httpMethod)
                    .tag("service", serviceName)
                    .tag("status", String.valueOf(statusCode))
                    .publishPercentiles(0.5, 0.9, 0.99) // p50, p90, p99
                    .publishPercentileHistogram()
                    .register(meterRegistry)
                    .record(Duration.ofMillis(durationMs));

            // Contador de peticiones por servicio
            meterRegistry.counter("gateway.requests.total",
                    "service", serviceName,
                    "method", httpMethod,
                    "status", String.valueOf(statusCode)
            ).increment();

            // Contador específico de errores
            if (statusCode >= 400) {
                meterRegistry.counter("gateway.requests.errors",
                        "service", serviceName,
                        "status", String.valueOf(statusCode)
                ).increment();
            }
        } catch (Exception e) {
            log.warn("Error recording metrics: {}", e.getMessage());
        }
    }

    /**
     * Extrae el nombre del servicio desde el endpoint
     */
    private String extractServiceName(String endpoint) {
        if (endpoint == null || endpoint.isEmpty()) {
            return "unknown";
        }

        // Formato esperado: /api/v1/service-name/...
        String[] parts = endpoint.split("/");
        if (parts.length >= 4) {
            return parts[3];
        }

        return "unknown";
    }

    /**
     * Obtiene la IP del cliente, considerando proxies
     */
    private String getClientIp(ServerHttpRequest request) {
        String ip = request.getHeaders().getFirst("X-Forwarded-For");
        if (ip != null && !ip.isEmpty()) {
            return ip.split(",")[0].trim();
        }

        ip = request.getHeaders().getFirst("X-Real-IP");
        if (ip != null && !ip.isEmpty()) {
            return ip;
        }

        if (request.getRemoteAddress() != null) {
            return request.getRemoteAddress().getAddress().getHostAddress();
        }

        return "unknown";
    }

    @Override
    public int getOrder() {
        // Ejecutar después del rate limiting (0) pero antes de otros filtros
        return 1;
    }
}
