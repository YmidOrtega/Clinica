package com.ClinicaDeYmid.commons.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.Optional;

public class CurrentUser {

    public Optional<AuthenticatedUser> get() {
        return currentJwt().flatMap(TokenSubjects::user);
    }

    public Optional<AuthenticatedService> service() {
        return currentJwt().flatMap(TokenSubjects::service);
    }

    public Optional<String> bearerToken() {
        return currentJwt().map(Jwt::getTokenValue);
    }

    private static Optional<Jwt> currentJwt() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication instanceof JwtAuthenticationToken token && token.isAuthenticated()
                ? Optional.of(token.getToken())
                : Optional.empty();
    }
}
