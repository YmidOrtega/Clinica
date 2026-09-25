package com.ClinicaDeYmid.billing_service.domain;

import java.time.Instant;

public sealed interface AccountStatus {

    enum Code {
        OPEN,
        FROZEN,
        VOIDED
    }

    record Open() implements AccountStatus {
    }

    record Frozen(DischargeType discharge, Instant since) implements AccountStatus {
        public Frozen {
            DomainRules.required(since, "since");
        }
    }

    record Voided(String reason, Instant since) implements AccountStatus {
        public Voided {
            reason = DomainRules.requiredText(reason, "reason", 500);
            DomainRules.required(since, "since");
        }
    }

    default Code code() {
        return switch (this) {
            case Open ignored -> Code.OPEN;
            case Frozen ignored -> Code.FROZEN;
            case Voided ignored -> Code.VOIDED;
        };
    }

    default boolean acceptsCharges() {
        return this instanceof Open;
    }
}
