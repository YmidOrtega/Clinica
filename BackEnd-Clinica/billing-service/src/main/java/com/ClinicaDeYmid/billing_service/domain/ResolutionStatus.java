package com.ClinicaDeYmid.billing_service.domain;

import java.time.Instant;

public sealed interface ResolutionStatus {

    enum Code {
        PENDING,
        ACTIVE,
        EXHAUSTED,
        RETIRED
    }

    record Pending() implements ResolutionStatus {
    }

    record Active(Instant since) implements ResolutionStatus {
        public Active {
            DomainRules.required(since, "since");
        }
    }

    record Exhausted(Instant since) implements ResolutionStatus {
        public Exhausted {
            DomainRules.required(since, "since");
        }
    }

    record Retired(String reason, Instant since) implements ResolutionStatus {
        public Retired {
            reason = DomainRules.requiredText(reason, "reason", 500);
            DomainRules.required(since, "since");
        }
    }

    default Code code() {
        return switch (this) {
            case Pending ignored -> Code.PENDING;
            case Active ignored -> Code.ACTIVE;
            case Exhausted ignored -> Code.EXHAUSTED;
            case Retired ignored -> Code.RETIRED;
        };
    }

    default ResolutionStatus activate(Instant now) {
        return switch (this) {
            case Pending ignored -> new Active(now);
            case ResolutionStatus other -> throw new BillingException.InvalidResolutionTransition(other.code(), Code.ACTIVE);
        };
    }

    default ResolutionStatus exhaust(Instant now) {
        return switch (this) {
            case Active ignored -> new Exhausted(now);
            case ResolutionStatus other -> throw new BillingException.InvalidResolutionTransition(other.code(), Code.EXHAUSTED);
        };
    }

    default ResolutionStatus retire(String reason, Instant now) {
        return switch (this) {
            case Pending ignored -> new Retired(reason, now);
            case Active ignored -> new Retired(reason, now);
            case ResolutionStatus other -> throw new BillingException.InvalidResolutionTransition(other.code(), Code.RETIRED);
        };
    }
}
