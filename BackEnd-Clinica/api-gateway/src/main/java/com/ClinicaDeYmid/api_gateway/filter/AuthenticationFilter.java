package com.ClinicaDeYmid.api_gateway.filter;

import com.ClinicaDeYmid.api_gateway.security.JwtValidatorService;
import com.ClinicaDeYmid.api_gateway.security.TokenBlacklistServiceGateway;
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

import java.util.logging.Logger;

@Component
public class AuthenticationFilter extends AbstractGatewayFilterFactory<AuthenticationFilter.Config> {

    private static final Logger logger = Logger.getLogger(AuthenticationFilter.class.getName());

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

            logger.info("Processing request to path: " + path);

            // Si la ruta NO requiere autenticación, permitir el paso
            if (!routeValidator.isSecured(request)) {
                logger.info("Path is not secured, allowing request: " + path);
                return chain.filter(exchange);
            }

            logger.info("Path requires authentication: " + path);

            // Verificar si existe el header Authorization
            if (!request.getHeaders().containsKey(HttpHeaders.AUTHORIZATION)) {
                return onError(exchange, "No se ha proporcionado el token de autenticación", HttpStatus.UNAUTHORIZED);
            }

            String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                return onError(exchange, "Formato de token inválido. Debe ser 'Bearer [token]'", HttpStatus.UNAUTHORIZED);
            }

            String token = authHeader.substring(7);

            return jwtValidatorService.validateAndDecodeToken(token)
                    .flatMap(decodedJWT -> {
                        return tokenBlacklistServiceGateway.isTokenBlacklisted(token)
                                .flatMap(isBlacklisted -> {
                                    if (isBlacklisted) {
                                        logger.warning("Token está en la blacklist");
                                        return onError(exchange, "Token inválido (revocado o en blacklist)", HttpStatus.UNAUTHORIZED);
                                    }

                                    String userId = decodedJWT.getSubject();
                                    String userEmail = decodedJWT.getClaim("email").asString();

                                    logger.info("Token validado exitosamente para usuario: " + userEmail);

                                    // Crear el request mutado con los headers adicionales
                                    ServerHttpRequest mutatedRequest = request.mutate()
                                            .header("X-User-ID", userId)
                                            .header("X-User-Email", userEmail)
                                            .build();

                                    // Crear el exchange mutado
                                    ServerWebExchange mutatedExchange = exchange.mutate()
                                            .request(mutatedRequest)
                                            .build();

                                    return chain.filter(mutatedExchange);
                                });
                    })
                    .onErrorResume(e -> {
                        logger.warning("Error de validación de token: " + e.getMessage());
                        // Si el mensaje contiene "no se pudo inicializar el algoritmo" o "Auth-Service está disponible", responde 503
                        if (e.getMessage() != null && e.getMessage().toLowerCase().contains("auth-service")) {
                            return onError(exchange, "El sistema de autenticación está temporalmente fuera de servicio. Intenta más tarde.", HttpStatus.SERVICE_UNAVAILABLE);
                        }
                        return onError(exchange, "Token de autenticación inválido", HttpStatus.UNAUTHORIZED);
                    });
        };
    }

    private Mono<Void> onError(ServerWebExchange exchange, String err, HttpStatus httpStatus) {
        logger.severe("API Gateway Security Error: " + err);

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
