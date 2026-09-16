package com.ClinicaDeYmid.contracting_service.domain;

import java.time.Instant;

public sealed interface PayerStatus {

    enum Code {
        ACTIVE,
        SUSPENDED,
        DEACTIVATED
    }

    record Active() implements PayerStatus {
    }

    record Suspended(String reason, Instant since) implements PayerStatus {
        public Suspended {
            reason = DomainRules.requiredText(reason, "reason", 500);
            DomainRules.required(since, "since");
        }
    }

    record Deactivated(String reason, Instant since) implements PayerStatus {
        public Deactivated {
            reason = DomainRules.requiredText(reason, "reason", 500);
            DomainRules.required(since, "since");
        }
    }

    default Code code() {
        return switch (this) {
            case Active active -> Code.ACTIVE;
            case Suspended suspended -> Code.SUSPENDED;
            case Deactivated deactivated -> Code.DEACTIVATED;
        };
    }

    default boolean contractable() {
        return this instanceof Active;
    }

    default PayerStatus suspend(String reason, Instant now) {
        return switch (this) {
            case Active active -> new Suspended(reason, now);
            case Suspended suspended -> throw rejected(Code.SUSPENDED);
            case Deactivated deactivated -> throw rejected(Code.SUSPENDED);
        };
    }

    default PayerStatus reactivate(Instant now) {
        return switch (this) {
            case Suspended suspended -> new Active();
            case Deactivated deactivated -> new Active();
            case Active active -> throw rejected(Code.ACTIVE);
        };
    }

    default PayerStatus deactivate(String reason, Instant now) {
        return switch (this) {
            case Active active -> new Deactivated(reason, now);
            case Suspended suspended -> new Deactivated(reason, now);
            case Deactivated deactivated -> throw rejected(Code.DEACTIVATED);
        };
    }

    private ContractingException.InvalidStatusTransition rejected(Code target) {
        return new ContractingException.InvalidStatusTransition(code(), target);
    }
}
