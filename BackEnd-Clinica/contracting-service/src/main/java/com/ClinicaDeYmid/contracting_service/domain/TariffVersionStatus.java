package com.ClinicaDeYmid.contracting_service.domain;

import java.time.Instant;

public sealed interface TariffVersionStatus {

    enum Code {
        DRAFT,
        ACTIVE,
        RETIRED
    }

    record Draft() implements TariffVersionStatus {
    }

    record Active(Instant since) implements TariffVersionStatus {
        public Active {
            DomainRules.required(since, "since");
        }
    }

    record Retired(Instant since) implements TariffVersionStatus {
        public Retired {
            DomainRules.required(since, "since");
        }
    }

    default Code code() {
        return switch (this) {
            case Draft draft -> Code.DRAFT;
            case Active active -> Code.ACTIVE;
            case Retired retired -> Code.RETIRED;
        };
    }

    default boolean editable() {
        return this instanceof Draft;
    }

    default TariffVersionStatus activate(Instant now) {
        return switch (this) {
            case Draft draft -> new Active(now);
            case Active active -> throw new ContractingException.TariffVersionNotEditable("ya está vigente");
            case Retired retired -> throw new ContractingException.TariffVersionNotEditable("ya fue retirada");
        };
    }

    default TariffVersionStatus retire(Instant now) {
        return switch (this) {
            case Active active -> new Retired(now);
            case Draft draft -> throw new ContractingException.TariffVersionNotEditable("todavía es un borrador");
            case Retired retired -> throw new ContractingException.TariffVersionNotEditable("ya fue retirada");
        };
    }
}
