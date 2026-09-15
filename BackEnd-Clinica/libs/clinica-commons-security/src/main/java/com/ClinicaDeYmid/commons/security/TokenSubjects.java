package com.ClinicaDeYmid.commons.security;

import org.springframework.security.oauth2.jwt.Jwt;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

final class TokenSubjects {

    private TokenSubjects() {
    }

    static boolean isService(Jwt jwt) {
        return jwt.getSubject() != null && jwt.getSubject().equals(jwt.getClaimAsString(ClinicaJwtClaims.CLIENT_ID));
    }

    static Optional<AuthenticatedUser> user(Jwt jwt) {
        if (jwt.getSubject() == null || isService(jwt) || jwt.getClaimAsString(ClinicaJwtClaims.ROLE) == null || jwt.getClaimAsInstant(ClinicaJwtClaims.AUTH_TIME) == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(new AuthenticatedUser(UUID.fromString(jwt.getSubject()), jwt.getClaimAsString(ClinicaJwtClaims.EMAIL),
                    jwt.getClaimAsString(ClinicaJwtClaims.NAME), jwt.getClaimAsString(ClinicaJwtClaims.ROLE),
                    jwt.getClaimAsInstant(ClinicaJwtClaims.AUTH_TIME),
                    Optional.ofNullable(jwt.getClaimAsStringList(ClinicaJwtClaims.AMR)).orElse(List.of()), actorsOf(jwt.getClaims())));
        } catch (IllegalArgumentException malformedSubject) {
            return Optional.empty();
        }
    }

    static Optional<AuthenticatedService> service(Jwt jwt) {
        return isService(jwt) ? Optional.of(new AuthenticatedService(jwt.getSubject(), scopesOf(jwt))) : Optional.empty();
    }

    static Set<String> scopesOf(Jwt jwt) {
        Object scope = jwt.getClaims().get(ClinicaJwtClaims.SCOPE);
        Set<String> scopes = new HashSet<>();
        if (scope instanceof String text) {
            scopes.addAll(List.of(text.trim().split("\\s+")));
        } else if (scope instanceof Iterable<?> values) {
            values.forEach(value -> scopes.add(String.valueOf(value)));
        }
        scopes.remove("");
        return scopes;
    }

    private static List<String> actorsOf(Map<String, Object> claims) {
        List<String> actors = new ArrayList<>();
        Object actor = claims.get(ClinicaJwtClaims.ACTOR);
        while (actor instanceof Map<?, ?> current) {
            actors.add(String.valueOf(current.get("sub")));
            actor = current.get(ClinicaJwtClaims.ACTOR);
        }
        return actors;
    }
}
