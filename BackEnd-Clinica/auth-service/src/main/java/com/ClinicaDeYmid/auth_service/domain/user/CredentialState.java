package com.ClinicaDeYmid.auth_service.domain.user;

import java.time.Instant;

public sealed interface CredentialState {

    enum Code {
        NOT_SET,
        CURRENT,
        CHANGE_REQUIRED
    }

    record NotSet() implements CredentialState {
    }

    record Current(Instant changedAt) implements CredentialState {
        public Current {
            DomainRules.required(changedAt, "changedAt");
        }
    }

    record ChangeRequired(Instant changedAt, String reason) implements CredentialState {
        public ChangeRequired {
            DomainRules.required(changedAt, "changedAt");
            reason = DomainRules.requiredText(reason, "reason", UserStatus.MIN_REASON_LENGTH, UserStatus.MAX_REASON_LENGTH);
        }
    }

    default Code code() {
        return switch (this) {
            case NotSet notSet -> Code.NOT_SET;
            case Current current -> Code.CURRENT;
            case ChangeRequired changeRequired -> Code.CHANGE_REQUIRED;
        };
    }
}
