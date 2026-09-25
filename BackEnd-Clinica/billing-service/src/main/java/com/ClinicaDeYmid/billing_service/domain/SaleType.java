package com.ClinicaDeYmid.billing_service.domain;

public sealed interface SaleType {

    enum Code {
        NON_SURGICAL
    }

    record NonSurgical() implements SaleType {
    }

    default Code code() {
        return switch (this) {
            case NonSurgical ignored -> Code.NON_SURGICAL;
        };
    }

    static SaleType of(Code code) {
        return switch (code) {
            case NON_SURGICAL -> new NonSurgical();
        };
    }
}
