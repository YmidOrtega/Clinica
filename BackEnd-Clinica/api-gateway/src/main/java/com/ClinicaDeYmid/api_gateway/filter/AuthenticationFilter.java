package com.ClinicaDeYmid.api_gateway.filter;

import com.ClinicaDeYmid.api_gateway.security.JwtValidatorService;
import com.ClinicaDeYmid.api_gateway.security.TokenBlacklistServiceGateway;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Slf4j
@Component
public class AuthenticationFilter extends AbstractGatewayFilterFactory<AuthenticationFilter.Config> {

    private final RouteValidator routeValidator;
    private final JwtValidatorService jwtValidatorService;
    private final TokenBlacklistServiceGateway tokenBlacklistServiceGateway;

    public AuthenticationFilter(RouteValidator routeValidator,
                                JwtValidatorService jwtValidatorService,
                                TokenBlacklistServiceGateway tokenBlacklistServiceGateway) {
        super(Config.class);
        this.routeValidator = routeValidator;
        this.jwtValidatorService = jwtValidatorService;
        this.tokenBlacklistServiceGateway = tokenBlacklistServiceGateway;
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            ServerHttpRequest request = exchange.getRequest();
            String path = request.getURI().getPath();

            log.debug("Processing request to path: {}", path);

            if (!routeValidator.isSecured(request)) {
                log.debug("Path is not secured, allowing request: {}", path);
                return chain.filter(exchange);
            }

            if (!request.getHeaders().containsKey(HttpHeaders.AUTHORIZATION)) {
                return onError(exchange, "No se ha proporcionado el token de autenticación", HttpStatus.UNAUTHORIZED);
            }

            String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                return onError(exchange, "Formato de token inválido. Debe ser 'Bearer [token]'", HttpStatus.UNAUTHORIZED);
            }

            String token = authHeader.substring(7);

            return jwtValidatorService.validateAndDecodeToken(token)
                    .flatMap(decodedJWT -> tokenBlacklistServiceGateway.isTokenBlacklisted(token)
                            .flatMap(isBlacklisted -> {
                                if (isBlacklisted) {
                                    log.warn("Token está en la blacklist");
                                    return onError(exchange, "Token inválido (revocado o en blacklist)", HttpStatus.UNAUTHORIZED);
                                }

                                String userId = decodedJWT.getSubject();
                                String userEmail = decodedJWT.getClaim("email").asString();

                                log.debug("Token validado exitosamente para usuario: {}", userEmail);

                                ServerHttpRequest mutatedRequest = request.mutate()
                                        .header("X-User-ID", userId)
                                        .header("X-User-Email", userEmail)
                                        .build();

                                return chain.filter(exchange.mutate().request(mutatedRequest).build());
                            }))
                    .onErrorResume(e -> {
                        log.warn("Error de validación de token: {}", e.getMessage());
                        if (e.getMessage() != null && e.getMessage().toLowerCase().contains("auth-service")) {
                            return onError(exchange, "El sistema de autenticación está temporalmente fuera de servicio. Intenta más tarde.", HttpStatus.SERVICE_UNAVAILABLE);
                        }
                        return onError(exchange, "Token de autenticación inválido", HttpStatus.UNAUTHORIZED);
                    });
        };
    }

    private Mono<Void> onError(ServerWebExchange exchange, String err, HttpStatus httpStatus) {
        log.error("API Gateway Security Error: {}", err);

        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(httpStatus);
        response.getHeaders().add("Content-Type", "application/json");

        String body = String.format("{\"error\":\"%s\",\"status\":%d,\"timestamp\":\"%s\"}",
                err, httpStatus.value(), java.time.Instant.now().toString());

        DataBuffer buffer = response.bufferFactory().wrap(body.getBytes());
        return response.writeWith(Mono.just(buffer));
    }

    public static class Config {}
}
