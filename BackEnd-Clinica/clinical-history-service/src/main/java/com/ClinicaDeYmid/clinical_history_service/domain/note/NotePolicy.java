package com.ClinicaDeYmid.clinical_history_service.domain.note;

import com.ClinicaDeYmid.clinical_history_service.domain.ClinicalException;
import com.ClinicaDeYmid.clinical_history_service.domain.encounter.Encounter;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

public record NotePolicy(Duration extemporaneousAfter, Duration clockSkewTolerance) {

    public NotePolicy {
        Objects.requireNonNull(extemporaneousAfter, "extemporaneousAfter");
        Objects.requireNonNull(clockSkewTolerance, "clockSkewTolerance");
        if (extemporaneousAfter.isNegative() || extemporaneousAfter.isZero() || clockSkewTolerance.isNegative()) {
            throw new IllegalArgumentException("extemporaneousAfter must be positive and clockSkewTolerance not negative");
        }
    }

    void requireValidOccurrence(Encounter encounter, Instant occurredAt, Instant now) {
        if (occurredAt.isAfter(now.plus(clockSkewTolerance))) {
            throw new ClinicalException.InvalidOccurrence("no puede estar en el futuro");
        }
        if (occurredAt.isBefore(encounter.openedAt().minus(clockSkewTolerance))) {
            throw new ClinicalException.InvalidOccurrence("no puede ser anterior a la apertura de la atención");
        }
    }

    boolean isExtemporaneous(Instant occurredAt, Instant recordedAt) {
        return Duration.between(occurredAt, recordedAt).compareTo(extemporaneousAfter) > 0;
    }
}
