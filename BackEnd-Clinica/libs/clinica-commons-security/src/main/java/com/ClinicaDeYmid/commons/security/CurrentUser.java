package com.ClinicaDeYmid.commons.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.Optional;

public class CurrentUser {

    public Optional<AuthenticatedUser> get() {
        return currentJwt().map(CurrentUser::toUser);
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

    private static AuthenticatedUser toUser(Jwt jwt) {
        Object userId = jwt.getClaims().get(ClinicaJwtClaims.USER_ID);
        return new AuthenticatedUser(
                jwt.getSubject(),
                userId instanceof Number number ? number.longValue() : null,
                jwt.getClaimAsString(ClinicaJwtClaims.EMAIL),
                jwt.getClaimAsString(ClinicaJwtClaims.ROLE));
    }
}
