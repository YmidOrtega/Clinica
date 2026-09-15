package com.ClinicaDeYmid.auth_service.application.login;

import java.util.UUID;

public sealed interface LoginOutcome {

    UUID userUuid();

    record PasswordChangeRequired(UUID userUuid) implements LoginOutcome {
    }

    record SecondFactorRequired(UUID userUuid) implements LoginOutcome {
    }

    record SecondFactorEnrollmentRequired(UUID userUuid) implements LoginOutcome {
    }
}
