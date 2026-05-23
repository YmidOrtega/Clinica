package com.ClinicaDeYmid.api_gateway.filter;

import com.ClinicaDeYmid.api_gateway.ratelimit.RateLimitService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimitFilter implements GlobalFilter, Ordered {

    private final RateLimitService rateLimitService;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String userId = request.getHeaders().getFirst("X-User-ID");
        String ipAddress = getClientIp(request);

        log.debug("Rate limit check - User: {}, IP: {}", userId, ipAddress);

        return rateLimitService.allowIp(ipAddress)
                .flatMap(ipAllowed -> {
                    if (!ipAllowed) {
                        log.warn("Rate limit excedido para IP: {}", ipAddress);
                        return rateLimitService.getRemainingForIp(ipAddress)
                                .flatMap(remaining -> handleRateLimitExceeded(
                                        exchange,
                                        "Límite de peticiones por IP excedido (máx: 1000/min)",
                                        remaining
                                ));
                    }

                    if (userId != null && !userId.isEmpty()) {
                        return rateLimitService.allowUser(userId)
                                .flatMap(userAllowed -> {
                                    if (!userAllowed) {
                                        log.warn("Rate limit excedido para usuario: {}", userId);
                                        return rateLimitService.getRemainingForUser(userId)
                                                .flatMap(remaining -> handleRateLimitExceeded(
                                                        exchange,
                                                        "Límite de peticiones por usuario excedido (máx: 100/min)",
                                                        remaining
                                                ));
                                    }
                                    return chain.filter(exchange);
                                });
                    }

                    return chain.filter(exchange);
                });
    }

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

    private Mono<Void> handleRateLimitExceeded(ServerWebExchange exchange, String message, long remainingTokens) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        response.getHeaders().add("X-RateLimit-Remaining", String.valueOf(remainingTokens));
        response.getHeaders().add("Retry-After", "60");

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
        return 0;
    }
}
