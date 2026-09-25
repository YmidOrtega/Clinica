package com.ClinicaDeYmid.billing_service.domain;

import java.time.Instant;

public sealed interface SaleStatus {

    enum Code {
        DRAFT,
        CONFIRMED,
        CANCELLED
    }

    record Draft() implements SaleStatus {
    }

    record Confirmed(Instant since) implements SaleStatus {
        public Confirmed {
            DomainRules.required(since, "since");
        }
    }

    record Cancelled(String reason, Instant since) implements SaleStatus {
        public Cancelled {
            reason = DomainRules.requiredText(reason, "reason", 500);
            DomainRules.required(since, "since");
        }
    }

    default Code code() {
        return switch (this) {
            case Draft ignored -> Code.DRAFT;
            case Confirmed ignored -> Code.CONFIRMED;
            case Cancelled ignored -> Code.CANCELLED;
        };
    }

    default boolean editable() {
        return this instanceof Draft;
    }

    default SaleStatus confirm(Instant now) {
        return switch (this) {
            case Draft ignored -> new Confirmed(now);
            case SaleStatus other -> throw new BillingException.InvalidSaleTransition(other.code(), Code.CONFIRMED);
        };
    }

    default SaleStatus cancel(String reason, Instant now) {
        return switch (this) {
            case Draft ignored -> new Cancelled(reason, now);
            case Confirmed ignored -> new Cancelled(reason, now);
            case Cancelled ignored -> throw new BillingException.InvalidSaleTransition(Code.CANCELLED, Code.CANCELLED);
        };
    }
}
