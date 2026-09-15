package com.ClinicaDeYmid.auth_service.infrastructure.security;

import com.ClinicaDeYmid.auth_service.application.StaffIdentity;
import com.ClinicaDeYmid.auth_service.domain.user.Role;

import java.io.Serializable;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record StaffPrincipal(UUID uuid, String email, String fullName, Role role, Instant authenticatedAt, List<String> methods)
        implements Serializable {

    public static final String PASSWORD = "pwd";

    public StaffPrincipal {
        methods = List.copyOf(methods);
    }

    public static StaffPrincipal withPassword(StaffIdentity identity) {
        return new StaffPrincipal(identity.uuid(), identity.email(), identity.fullName(), identity.role(), identity.authenticatedAt(),
                List.of(PASSWORD));
    }
}
