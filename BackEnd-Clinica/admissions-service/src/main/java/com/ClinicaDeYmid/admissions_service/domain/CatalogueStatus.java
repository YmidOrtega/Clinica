package com.ClinicaDeYmid.admissions_service.domain;

import java.time.Instant;

public sealed interface CatalogueStatus {

    enum Code {
        ACTIVE,
        RETIRED
    }

    record Active() implements CatalogueStatus {
    }

    record Retired(String reason, Instant since) implements CatalogueStatus {
        public Retired {
            reason = DomainRules.requiredText(reason, "reason", 500);
            DomainRules.required(since, "since");
        }
    }

    default Code code() {
        return this instanceof Active ? Code.ACTIVE : Code.RETIRED;
    }

    default boolean usable() {
        return this instanceof Active;
    }

    default CatalogueStatus retire(String reason, Instant now) {
        return switch (this) {
            case Active ignored -> new Retired(reason, now);
            case Retired ignored -> throw new AdmissionsException.AlreadyRetired();
        };
    }

    default CatalogueStatus restore() {
        return switch (this) {
            case Retired ignored -> new Active();
            case Active ignored -> throw new AdmissionsException.AlreadyActive();
        };
    }
}
