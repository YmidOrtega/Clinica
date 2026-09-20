package com.ClinicaDeYmid.admissions_service.domain;

import java.time.Instant;

public sealed interface AuthorizationStatus {

    enum Code {
        ACTIVE,
        REVOKED
    }

    record Active() implements AuthorizationStatus {
    }

    record Revoked(String reason, Instant at) implements AuthorizationStatus {
        public Revoked {
            reason = DomainRules.requiredText(reason, "reason", 500);
            DomainRules.required(at, "at");
        }
    }

    default Code code() {
        return this instanceof Active ? Code.ACTIVE : Code.REVOKED;
    }

    default boolean usable() {
        return this instanceof Active;
    }

    default AuthorizationStatus revoke(String reason, Instant now) {
        return switch (this) {
            case Active ignored -> new Revoked(reason, now);
            case Revoked ignored -> throw new AdmissionsException.AuthorizationAlreadyRevoked();
        };
    }
}
