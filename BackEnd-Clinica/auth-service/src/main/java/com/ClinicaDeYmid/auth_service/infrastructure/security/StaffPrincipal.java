package com.ClinicaDeYmid.auth_service.infrastructure.security;

import com.ClinicaDeYmid.auth_service.application.StaffIdentity;
import com.ClinicaDeYmid.auth_service.domain.secondfactor.AuthenticationMethod;
import com.ClinicaDeYmid.auth_service.domain.user.Role;

import java.io.Serializable;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record StaffPrincipal(UUID uuid, String email, String fullName, Role role, Instant authenticatedAt, List<String> methods,
                             Instant sessionStartedAt) implements Serializable {

    public static final String ACR_MULTI_FACTOR = "urn:clinica:acr:mfa";

    public StaffPrincipal {
        methods = List.copyOf(methods);
        sessionStartedAt = sessionStartedAt == null ? authenticatedAt : sessionStartedAt;
    }

    public static StaffPrincipal of(StaffIdentity identity, Instant sessionStartedAt) {
        return new StaffPrincipal(identity.uuid(), identity.email(), identity.fullName(), identity.role(), identity.authenticatedAt(),
                identity.methods().stream().map(AuthenticationMethod::amr).toList(), sessionStartedAt);
    }

    public boolean multiFactor() {
        return methods.contains(AuthenticationMethod.MULTI_FACTOR.amr());
    }
}
