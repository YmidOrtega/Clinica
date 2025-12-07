package com.ClinicaDeYmid.api_gateway.filter;

import com.ClinicaDeYmid.api_gateway.ratelimit.RateLimitService;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.logging.Logger;

/**
 * Filtro global para implementar rate limiting
 * Limita las peticiones por usuario (100/min) e IP (1000/min)
 */
@Component
public class RateLimitFilter implements GlobalFilter, Ordered {

    private static final Logger logger = Logger.getLogger(RateLimitFilter.class.getName());
    private final RateLimitService rateLimitService;

    public RateLimitFilter(RateLimitService rateLimitService) {
        this.rateLimitService = rateLimitService;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();

        // Obtener información del usuario y la IP
        String userId = request.getHeaders().getFirst("X-User-ID");
        String ipAddress = getClientIp(request);

        logger.info("Rate limit check - User: " + userId + ", IP: " + ipAddress);

        // Verificar límite por IP (siempre se aplica)
        if (!rateLimitService.allowIp(ipAddress)) {
            logger.warning("Rate limit exceeded for IP: " + ipAddress);
            return handleRateLimitExceeded(
                    exchange,
                    "Límite de peticiones por IP excedido (máx: 1000/min)",
                    rateLimitService.getRemainingSecondsForIp(ipAddress)
            );
        }

        // Verificar límite por usuario (solo si está autenticado)
        if (userId != null && !userId.isEmpty()) {
            if (!rateLimitService.allowUser(userId)) {
                logger.warning("Rate limit exceeded for user: " + userId);
                return handleRateLimitExceeded(
                        exchange,
                        "Límite de peticiones por usuario excedido (máx: 100/min)",
                        rateLimitService.getRemainingSecondsForUser(userId)
                );
            }
        }

        return chain.filter(exchange);
    }

    /**
     * Obtiene la IP del cliente, considerando proxies
     */
    private String getClientIp(ServerHttpRequest request) {
        String ip = request.getHeaders().getFirst("X-Forwarded-For");
        if (ip != null && !ip.isEmpty()) {
            // X-Forwarded-For puede contener múltiples IPs, tomamos la primera
            return ip.split(",")[0].trim();
        }

        ip = request.getHeaders().getFirst("X-Real-IP");
        if (ip != null && !ip.isEmpty()) {
            return ip;
        }

        // Fallback a la IP remota
        if (request.getRemoteAddress() != null) {
            return request.getRemoteAddress().getAddress().getHostAddress();
        }

        return "unknown";
    }

    /**
     * Maneja la respuesta cuando se excede el rate limit
     */
    private Mono<Void> handleRateLimitExceeded(
            ServerWebExchange exchange,
            String message,
            long remainingTokens
    ) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        response.getHeaders().add("X-RateLimit-Remaining", String.valueOf(remainingTokens));
        response.getHeaders().add("Retry-After", "60"); // Reintentar después de 60 segundos

        String errorResponse = String.format(
                "{\"error\":\"%s\",\"status\":429,\"timestamp\":\"%s\",\"remainingTokens\":%d}",
                message,
                java.time.Instant.now().toString(),
                remainingTokens
        );

        DataBuffer buffer = response.bufferFactory()
                .wrap(errorResponse.getBytes(StandardCharsets.UTF_8));

        return response.writeWith(Mono.just(buffer));
    }

    @Override
    public int getOrder() {
        // Ejecutar después de la autenticación (-1) pero antes del logging (1)
        return 0;
    }
}
