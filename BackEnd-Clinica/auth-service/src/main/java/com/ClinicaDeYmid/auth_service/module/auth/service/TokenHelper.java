package com.ClinicaDeYmid.auth_service.module.auth.service;

import org.springframework.stereotype.Component;

@Component
public class TokenHelper {

    private static final String BEARER_PREFIX = "Bearer ";

    public String extractToken(String authHeader) {
        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            throw new IllegalArgumentException("Header de autorización inválido");
        }

        String token = authHeader.substring(BEARER_PREFIX.length()).trim();

        if (token.isEmpty()) {
            throw new IllegalArgumentException("Token vacío");
        }

        return token;
    }
}
