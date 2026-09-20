package com.ClinicaDeYmid.practitioners_service.repository.entity;

import com.ClinicaDeYmid.practitioners_service.shared.PractitionersException;
import com.ClinicaDeYmid.practitioners_service.shared.Rules;

import java.time.Instant;

public sealed interface CatalogueStatus {

    enum Code {
        ACTIVE,
        INACTIVE
    }

    record Active() implements CatalogueStatus {
    }

    record Inactive(String reason, Instant since) implements CatalogueStatus {
        public Inactive {
            reason = Rules.atLeast(Rules.requiredText(reason, "reason", 500), 10, "reason");
            Rules.required(since, "since");
        }
    }

    default Code code() {
        return this instanceof Active ? Code.ACTIVE : Code.INACTIVE;
    }

    default boolean active() {
        return this instanceof Active;
    }

    default CatalogueStatus deactivate(String reason, Instant now) {
        return switch (this) {
            case Active ignored -> new Inactive(reason, now);
            case Inactive ignored -> throw new PractitionersException.NotActive();
        };
    }

    default CatalogueStatus reactivate() {
        return switch (this) {
            case Inactive ignored -> new Active();
            case Active ignored -> throw new PractitionersException.AlreadyActive();
        };
    }
}
