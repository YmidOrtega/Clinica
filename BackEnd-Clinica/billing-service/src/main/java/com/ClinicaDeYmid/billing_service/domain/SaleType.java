package com.ClinicaDeYmid.billing_service.domain;

import java.time.LocalDate;

public sealed interface SaleType {

    enum Code {
        NON_SURGICAL,
        SURGICAL
    }

    record NonSurgical() implements SaleType {
    }

    record Surgical(LocalDate performedOn) implements SaleType {
        public Surgical {
            DomainRules.required(performedOn, "performedOn");
        }
    }

    default Code code() {
        return switch (this) {
            case NonSurgical ignored -> Code.NON_SURGICAL;
            case Surgical ignored -> Code.SURGICAL;
        };
    }
}
