package com.ClinicaDeYmid.auth_service.application;

import com.ClinicaDeYmid.auth_service.domain.secondfactor.AuthenticationMethod;
import com.ClinicaDeYmid.auth_service.domain.user.Role;
import com.ClinicaDeYmid.auth_service.domain.user.User;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record StaffIdentity(UUID uuid, String email, String fullName, Role role, Instant authenticatedAt, List<AuthenticationMethod> methods) {

    public StaffIdentity {
        methods = List.copyOf(methods);
    }

    public static StaffIdentity of(User user, Instant authenticatedAt, List<AuthenticationMethod> methods) {
        return new StaffIdentity(user.uuid(), user.email().value(), user.fullName().value(), user.role(), authenticatedAt, methods);
    }
}
