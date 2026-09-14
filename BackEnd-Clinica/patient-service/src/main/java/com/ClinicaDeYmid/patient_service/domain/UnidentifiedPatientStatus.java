package com.ClinicaDeYmid.patient_service.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public sealed interface UnidentifiedPatientStatus {

    enum Code {
        UNIDENTIFIED,
        IDENTIFIED,
        DECEASED
    }

    record Unidentified() implements UnidentifiedPatientStatus {
    }

    record Identified(UUID patientUuid, String reason, Instant since) implements UnidentifiedPatientStatus {
        public Identified {
            DomainRules.required(patientUuid, "patientUuid");
            reason = DomainRules.requiredText(reason, "reason", 500);
            DomainRules.required(since, "since");
        }
    }

    record Deceased(LocalDate dateOfDeath) implements UnidentifiedPatientStatus {
        public Deceased {
            DomainRules.required(dateOfDeath, "dateOfDeath");
        }
    }

    default Code code() {
        return switch (this) {
            case Unidentified unidentified -> Code.UNIDENTIFIED;
            case Identified identified -> Code.IDENTIFIED;
            case Deceased deceased -> Code.DECEASED;
        };
    }

    default UnidentifiedPatientStatus identifyAs(UUID patientUuid, String reason, Instant now) {
        return switch (this) {
            case Unidentified unidentified -> new Identified(patientUuid, reason, now);
            case Identified identified -> throw rejected(Code.IDENTIFIED);
            case Deceased deceased -> throw rejected(Code.IDENTIFIED);
        };
    }

    default UnidentifiedPatientStatus revertIdentification() {
        return switch (this) {
            case Identified identified -> new Unidentified();
            case Unidentified unidentified -> throw rejected(Code.UNIDENTIFIED);
            case Deceased deceased -> throw rejected(Code.UNIDENTIFIED);
        };
    }

    default UnidentifiedPatientStatus die(LocalDate dateOfDeath) {
        return switch (this) {
            case Unidentified unidentified -> new Deceased(dateOfDeath);
            case Identified identified -> throw rejected(Code.DECEASED);
            case Deceased deceased -> throw rejected(Code.DECEASED);
        };
    }

    private PatientException.InvalidUnidentifiedStatusTransition rejected(Code target) {
        return new PatientException.InvalidUnidentifiedStatusTransition(code(), target);
    }
}
