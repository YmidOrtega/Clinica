package com.ClinicaDeYmid.admissions_service.domain.patient;

import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public sealed interface PatientReference {

    UUID uuid();

    long version();

    Sex sex();

    boolean admissible();

    String label();

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
            String payerUuid) implements PatientReference {

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
        public boolean admissible() {
            return status == Status.ACTIVE;
        }

        @Override
        public String label() {
            return firstNames + " " + lastNames;
        }

        public Optional<UUID> payer() {
            return Optional.ofNullable(payerUuid).map(UUID::fromString);
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
        public boolean admissible() {
            return status == Status.UNIDENTIFIED;
        }

        @Override
        public String label() {
            return code;
        }

        public Optional<UUID> identifiedAs() {
            return Optional.ofNullable(identifiedPatientUuid);
        }
    }
}
