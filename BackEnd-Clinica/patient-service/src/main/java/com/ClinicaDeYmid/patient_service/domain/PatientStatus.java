package com.ClinicaDeYmid.patient_service.domain;

import java.time.Instant;
import java.time.LocalDate;

public sealed interface PatientStatus {

    enum Code {
        ACTIVE,
        INACTIVE,
        DECEASED
    }

    record Active() implements PatientStatus {
    }

    record Inactive(String reason, Instant since) implements PatientStatus {
        public Inactive {
            reason = DomainRules.requiredText(reason, "reason", 500);
            DomainRules.required(since, "since");
        }
    }

    record Deceased(LocalDate dateOfDeath) implements PatientStatus {
        public Deceased {
            DomainRules.required(dateOfDeath, "dateOfDeath");
        }
    }

    default Code code() {
        return switch (this) {
            case Active active -> Code.ACTIVE;
            case Inactive inactive -> Code.INACTIVE;
            case Deceased deceased -> Code.DECEASED;
        };
    }

    default PatientStatus deactivate(String reason, Instant now) {
        return switch (this) {
            case Active active -> new Inactive(reason, now);
            case Inactive inactive -> throw rejected(Code.INACTIVE);
            case Deceased deceased -> throw rejected(Code.INACTIVE);
        };
    }

    default PatientStatus reactivate() {
        return switch (this) {
            case Inactive inactive -> new Active();
            case Active active -> throw rejected(Code.ACTIVE);
            case Deceased deceased -> throw rejected(Code.ACTIVE);
        };
    }

    default PatientStatus die(LocalDate dateOfDeath) {
        return switch (this) {
            case Active active -> new Deceased(dateOfDeath);
            case Inactive inactive -> new Deceased(dateOfDeath);
            case Deceased deceased -> throw rejected(Code.DECEASED);
        };
    }

    private PatientException.InvalidStatusTransition rejected(Code target) {
        return new PatientException.InvalidStatusTransition(code(), target);
    }
}
