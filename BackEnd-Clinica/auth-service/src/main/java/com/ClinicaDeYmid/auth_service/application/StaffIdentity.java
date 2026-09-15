package com.ClinicaDeYmid.auth_service.application;

import com.ClinicaDeYmid.auth_service.domain.user.Role;
import com.ClinicaDeYmid.auth_service.domain.user.User;

import java.time.Instant;
import java.util.UUID;

public record StaffIdentity(UUID uuid, String email, String fullName, Role role, Instant authenticatedAt) {

    public static StaffIdentity of(User user, Instant authenticatedAt) {
        return new StaffIdentity(user.uuid(), user.email().value(), user.fullName().value(), user.role(), authenticatedAt);
    }
}
