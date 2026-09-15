package com.ClinicaDeYmid.auth_service.domain.user;

import java.time.Instant;

public sealed interface SecondFactorState {

    enum Code {
        NOT_ENROLLED,
        TOTP_ENROLLED
    }

    record NotEnrolled() implements SecondFactorState {
    }

    record TotpEnrolled(Instant enrolledAt) implements SecondFactorState {
        public TotpEnrolled {
            DomainRules.required(enrolledAt, "enrolledAt");
        }
    }

    default Code code() {
        return switch (this) {
            case NotEnrolled notEnrolled -> Code.NOT_ENROLLED;
            case TotpEnrolled enrolled -> Code.TOTP_ENROLLED;
        };
    }
}
