package com.ClinicaDeYmid.api_gateway.filter;

import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.Predicate;
import java.util.logging.Logger;

@Component
public class RouteValidator {

    private static final Logger logger = Logger.getLogger(RouteValidator.class.getName());

    public static final List<String> openApiEndpoints = List.of(
            "/api/v1/auth/login",
            "/api/v1/auth/public-key",
            "/eureka/**",
            "/actuator/health"
    );

    public Predicate<ServerHttpRequest> isSecured = request -> {
        String requestPath = request.getURI().getPath();

        boolean isSecured = openApiEndpoints.stream()
                .noneMatch(endpoint -> matchesEndpoint(requestPath, endpoint));

        logger.info("Path: " + requestPath + " - Is secured: " + isSecured);
        return isSecured;
    };

    private boolean matchesEndpoint(String requestPath, String endpoint) {
        if (endpoint.endsWith("/**")) {
            // Para patrones como /eureka/**
            String prefix = endpoint.substring(0, endpoint.length() - 3);
            boolean matches = requestPath.startsWith(prefix);
            return matches;
        } else {
            // Para rutas exactas
            boolean matches = requestPath.equals(endpoint);
            return matches;
        }
    }
}

