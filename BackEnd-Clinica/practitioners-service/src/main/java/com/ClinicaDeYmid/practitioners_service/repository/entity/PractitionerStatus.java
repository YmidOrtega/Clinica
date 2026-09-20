package com.ClinicaDeYmid.practitioners_service.repository.entity;

import com.ClinicaDeYmid.practitioners_service.shared.PractitionerStatusCode;
import com.ClinicaDeYmid.practitioners_service.shared.PractitionersException;
import com.ClinicaDeYmid.practitioners_service.shared.Rules;

import java.time.Instant;

public sealed interface PractitionerStatus {

    record Active() implements PractitionerStatus {
    }

    record Suspended(String reason, Instant since) implements PractitionerStatus {
        public Suspended {
            reason = Rules.atLeast(Rules.requiredText(reason, "reason", 500), 10, "reason");
            Rules.required(since, "since");
        }
    }

    record Retired(String reason, Instant since) implements PractitionerStatus {
        public Retired {
            reason = Rules.atLeast(Rules.requiredText(reason, "reason", 500), 10, "reason");
            Rules.required(since, "since");
        }
    }

    default PractitionerStatusCode code() {
        return switch (this) {
            case Active ignored -> PractitionerStatusCode.ACTIVE;
            case Suspended ignored -> PractitionerStatusCode.SUSPENDED;
            case Retired ignored -> PractitionerStatusCode.RETIRED;
        };
    }

    default boolean attends() {
        return this instanceof Active;
    }

    default PractitionerStatus suspend(String reason, Instant now) {
        return switch (this) {
            case Active ignored -> new Suspended(reason, now);
            case Suspended ignored -> throw new PractitionersException.InvalidStatusTransition(code(), PractitionerStatusCode.SUSPENDED);
            case Retired ignored -> throw new PractitionersException.InvalidStatusTransition(code(), PractitionerStatusCode.SUSPENDED);
        };
    }

    default PractitionerStatus retire(String reason, Instant now) {
        return switch (this) {
            case Active ignored -> new Retired(reason, now);
            case Suspended ignored -> new Retired(reason, now);
            case Retired ignored -> throw new PractitionersException.InvalidStatusTransition(code(), PractitionerStatusCode.RETIRED);
        };
    }

    default PractitionerStatus reinstate() {
        return switch (this) {
            case Suspended ignored -> new Active();
            case Retired ignored -> new Active();
            case Active ignored -> throw new PractitionersException.InvalidStatusTransition(code(), PractitionerStatusCode.ACTIVE);
        };
    }
}
