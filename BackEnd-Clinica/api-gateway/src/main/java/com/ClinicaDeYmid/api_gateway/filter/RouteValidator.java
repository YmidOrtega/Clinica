package com.ClinicaDeYmid.api_gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
public class RouteValidator {

    public static final List<String> openApiEndpoints = List.of(
            "/api/v1/auth/login",
            "/api/v1/auth/public-key",
            "/eureka/**",
            "/actuator/health",
            "/v3/api-docs/**",
            "/swagger-ui/**"
    );

    public boolean isSecured(ServerHttpRequest request) {
        String requestPath = request.getURI().getPath();

        boolean secured = openApiEndpoints.stream()
                .noneMatch(endpoint -> matchesEndpoint(requestPath, endpoint));

        log.debug("Path: {} - Is secured: {}", requestPath, secured);
        return secured;
    }

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

