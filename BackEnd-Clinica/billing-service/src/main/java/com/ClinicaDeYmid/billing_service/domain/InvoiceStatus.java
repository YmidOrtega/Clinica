package com.ClinicaDeYmid.billing_service.domain;

import java.time.Instant;

public sealed interface InvoiceStatus {

    enum Code {
        DRAFT,
        ISSUED,
        DISCARDED,
        VOIDED
    }

    record Draft() implements InvoiceStatus {
    }

    record Issued(Instant at) implements InvoiceStatus {
        public Issued {
            DomainRules.required(at, "at");
        }
    }

    record Discarded(String reason, Instant at) implements InvoiceStatus {
        public Discarded {
            reason = DomainRules.requiredText(reason, "reason", 500);
            DomainRules.required(at, "at");
        }
    }

    record Voided(String reason, Instant at) implements InvoiceStatus {
        public Voided {
            reason = DomainRules.requiredText(reason, "reason", 500);
            DomainRules.required(at, "at");
        }
    }

    default Code code() {
        return switch (this) {
            case Draft ignored -> Code.DRAFT;
            case Issued ignored -> Code.ISSUED;
            case Discarded ignored -> Code.DISCARDED;
            case Voided ignored -> Code.VOIDED;
        };
    }

    default InvoiceStatus issue(Instant now) {
        return switch (this) {
            case Draft ignored -> new Issued(now);
            case InvoiceStatus other -> throw new BillingException.InvalidInvoiceTransition(other.code(), Code.ISSUED);
        };
    }

    default InvoiceStatus discard(String reason, Instant now) {
        return switch (this) {
            case Draft ignored -> new Discarded(reason, now);
            case InvoiceStatus other -> throw new BillingException.InvalidInvoiceTransition(other.code(), Code.DISCARDED);
        };
    }

    default InvoiceStatus voidBy(String reason, Instant now) {
        return switch (this) {
            case Issued ignored -> new Voided(reason, now);
            case InvoiceStatus other -> throw new BillingException.InvalidInvoiceTransition(other.code(), Code.VOIDED);
        };
    }
}
