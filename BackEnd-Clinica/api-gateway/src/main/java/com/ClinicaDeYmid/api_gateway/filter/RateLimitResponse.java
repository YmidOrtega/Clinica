package com.ClinicaDeYmid.api_gateway.filter;

import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

/**
 * Respuesta 429 compartida por los dos filtros de rate limiting.
 */
final class RateLimitResponse {

    private RateLimitResponse() {}

    static Mono<Void> tooManyRequests(ServerWebExchange exchange, String message, long remaining) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        response.getHeaders().add("X-RateLimit-Remaining", String.valueOf(remaining));
        response.getHeaders().add("Retry-After", "60");

        String body = String.format(
                "{\"error\":\"%s\",\"status\":429,\"timestamp\":\"%s\",\"remainingTokens\":%d}",
                message,
                java.time.Instant.now().toString(),
                remaining
        );

        DataBuffer buffer = response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }
}
