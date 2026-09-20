package com.ClinicaDeYmid.admissions_service.domain;

import java.time.Instant;
import java.util.UUID;

public sealed interface BedStatus {

    enum Code {
        AVAILABLE,
        OCCUPIED,
        CLEANING,
        MAINTENANCE,
        BLOCKED
    }

    record Available() implements BedStatus {
    }

    record Occupied(UUID occupant, Instant since) implements BedStatus {
        public Occupied {
            DomainRules.required(occupant, "occupant");
            DomainRules.required(since, "since");
        }
    }

    record Cleaning(Instant since) implements BedStatus {
        public Cleaning {
            DomainRules.required(since, "since");
        }
    }

    record Maintenance(String reason, Instant since) implements BedStatus {
        public Maintenance {
            reason = DomainRules.requiredText(reason, "reason", 500);
            DomainRules.required(since, "since");
        }
    }

    record Blocked(String reason, Instant since) implements BedStatus {
        public Blocked {
            reason = DomainRules.requiredText(reason, "reason", 500);
            DomainRules.required(since, "since");
        }
    }

    default Code code() {
        return switch (this) {
            case Available ignored -> Code.AVAILABLE;
            case Occupied ignored -> Code.OCCUPIED;
            case Cleaning ignored -> Code.CLEANING;
            case Maintenance ignored -> Code.MAINTENANCE;
            case Blocked ignored -> Code.BLOCKED;
        };
    }

    default boolean free() {
        return this instanceof Available;
    }

    default boolean taken() {
        return this instanceof Occupied;
    }

    default BedStatus occupy(UUID occupant, Instant now) {
        return switch (this) {
            case Available ignored -> new Occupied(occupant, now);
            case BedStatus other -> throw new AdmissionsException.BedNotAvailable(other.code());
        };
    }

    default BedStatus release(Instant now) {
        return switch (this) {
            case Occupied ignored -> new Cleaning(now);
            case BedStatus other -> throw new AdmissionsException.BedNotOccupied();
        };
    }

    default BedStatus finishCleaning() {
        return switch (this) {
            case Cleaning ignored -> new Available();
            case BedStatus other -> throw new AdmissionsException.BedNotAvailable(other.code());
        };
    }

    default BedStatus sendToMaintenance(String reason, Instant now) {
        return switch (this) {
            case Occupied occupied -> throw new AdmissionsException.BedNotAvailable(Code.OCCUPIED);
            case BedStatus other -> new Maintenance(reason, now);
        };
    }

    default BedStatus block(String reason, Instant now) {
        return switch (this) {
            case Occupied occupied -> throw new AdmissionsException.BedNotAvailable(Code.OCCUPIED);
            case BedStatus other -> new Blocked(reason, now);
        };
    }

    default BedStatus returnToService() {
        return switch (this) {
            case Maintenance ignored -> new Available();
            case Blocked ignored -> new Available();
            case BedStatus other -> throw new AdmissionsException.BedNotAvailable(other.code());
        };
    }
}
