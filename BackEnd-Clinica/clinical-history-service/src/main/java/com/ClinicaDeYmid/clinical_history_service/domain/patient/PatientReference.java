package com.ClinicaDeYmid.clinical_history_service.domain.patient;

import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public sealed interface PatientReference {

    UUID uuid();

    long version();

    Sex sex();

    boolean acceptsNewEncounters();

    enum Sex {
        FEMALE,
        MALE,
        INDETERMINATE
    }

    record Document(String type, String number) {
        public Document {
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(number, "number");
        }
    }

    record Registered(
            UUID uuid,
            long version,
            Document document,
            String firstNames,
            String lastNames,
            LocalDate birthDate,
            Sex sex,
            Status status,
            LocalDate dateOfDeath,
            String healthRegime,
            String healthProviderNit) implements PatientReference {

        public enum Status {
            ACTIVE,
            INACTIVE,
            DECEASED
        }

        public Registered {
            Objects.requireNonNull(uuid, "uuid");
            Objects.requireNonNull(document, "document");
            Objects.requireNonNull(firstNames, "firstNames");
            Objects.requireNonNull(lastNames, "lastNames");
            Objects.requireNonNull(birthDate, "birthDate");
            Objects.requireNonNull(sex, "sex");
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(healthRegime, "healthRegime");
        }

        @Override
        public boolean acceptsNewEncounters() {
            return status == Status.ACTIVE;
        }
    }

    record Unidentified(
            UUID uuid,
            long version,
            String code,
            Sex sex,
            int estimatedBirthYear,
            Status status,
            UUID identifiedPatientUuid,
            LocalDate dateOfDeath) implements PatientReference {

        public enum Status {
            UNIDENTIFIED,
            IDENTIFIED,
            DECEASED
        }

        public Unidentified {
            Objects.requireNonNull(uuid, "uuid");
            Objects.requireNonNull(code, "code");
            Objects.requireNonNull(sex, "sex");
            Objects.requireNonNull(status, "status");
            if ((status == Status.IDENTIFIED) != (identifiedPatientUuid != null)) {
                throw new IllegalArgumentException("identifiedPatientUuid must be present only when IDENTIFIED");
            }
        }

        @Override
        public boolean acceptsNewEncounters() {
            return status == Status.UNIDENTIFIED;
        }

        public Optional<UUID> identifiedAs() {
            return Optional.ofNullable(identifiedPatientUuid);
        }
    }
}
