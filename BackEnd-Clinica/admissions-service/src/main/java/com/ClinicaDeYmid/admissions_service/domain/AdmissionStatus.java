package com.ClinicaDeYmid.admissions_service.domain;

import java.time.Instant;

public sealed interface AdmissionStatus {

    enum Code {
        REGISTERED,
        ACTIVE,
        DISCHARGED,
        CANCELLED
    }

    record Registered() implements AdmissionStatus {
    }

    record Active(Instant since) implements AdmissionStatus {
        public Active {
            DomainRules.required(since, "since");
        }
    }

    record Discharged(Discharge discharge) implements AdmissionStatus {
        public Discharged {
            DomainRules.required(discharge, "discharge");
        }

        public Instant at() {
            return discharge.at();
        }
    }

    record Cancelled(String reason, Instant at) implements AdmissionStatus {
        public Cancelled {
            reason = DomainRules.requiredText(reason, "reason", 500);
            DomainRules.required(at, "at");
        }
    }

    default Code code() {
        return switch (this) {
            case Registered ignored -> Code.REGISTERED;
            case Active ignored -> Code.ACTIVE;
            case Discharged ignored -> Code.DISCHARGED;
            case Cancelled ignored -> Code.CANCELLED;
        };
    }

    default boolean open() {
        return this instanceof Registered || this instanceof Active;
    }

    default boolean editable() {
        return open();
    }

    default AdmissionStatus activate(Instant now) {
        return switch (this) {
            case Registered ignored -> new Active(now);
            case AdmissionStatus other -> throw new AdmissionsException.InvalidAdmissionTransition(other.code(), Code.ACTIVE);
        };
    }

    default AdmissionStatus discharge(Discharge discharge) {
        return switch (this) {
            case Active ignored -> new Discharged(DomainRules.required(discharge, "discharge"));
            case AdmissionStatus other -> throw new AdmissionsException.InvalidAdmissionTransition(other.code(), Code.DISCHARGED);
        };
    }

    default AdmissionStatus cancel(String reason, Instant now) {
        return switch (this) {
            case Registered ignored -> new Cancelled(reason, now);
            case Active ignored -> new Cancelled(reason, now);
            case AdmissionStatus other -> throw new AdmissionsException.InvalidAdmissionTransition(other.code(), Code.CANCELLED);
        };
    }
}
