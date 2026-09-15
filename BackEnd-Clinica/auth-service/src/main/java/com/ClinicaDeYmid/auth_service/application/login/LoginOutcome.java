package com.ClinicaDeYmid.auth_service.application.login;

import com.ClinicaDeYmid.auth_service.application.StaffIdentity;

import java.util.UUID;

public sealed interface LoginOutcome {

    record Authenticated(StaffIdentity identity) implements LoginOutcome {
    }

    record PasswordChangeRequired(UUID userUuid) implements LoginOutcome {
    }
}
