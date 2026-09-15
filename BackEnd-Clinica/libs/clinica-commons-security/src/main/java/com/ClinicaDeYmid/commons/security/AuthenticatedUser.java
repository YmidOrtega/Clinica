package com.ClinicaDeYmid.commons.security;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AuthenticatedUser(UUID uuid, String email, String name, String role, Instant authenticatedAt, List<String> methods,
                                List<String> actingServices) {

    public AuthenticatedUser {
        methods = List.copyOf(methods);
        actingServices = List.copyOf(actingServices);
    }

    public boolean multiFactor() {
        return methods.contains(ClinicaJwtClaims.MULTI_FACTOR);
    }

    public boolean delegated() {
        return !actingServices.isEmpty();
    }
}
