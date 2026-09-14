package com.ClinicaDeYmid.clinical_history_service.domain.note;

import com.ClinicaDeYmid.clinical_history_service.domain.ClinicalException;
import com.ClinicaDeYmid.clinical_history_service.domain.clinician.Signer;
import com.ClinicaDeYmid.clinical_history_service.domain.encounter.Encounter;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

public record NotePolicy(Duration extemporaneousAfter, Duration clockSkewTolerance, Duration maxAuthenticationAge) {

    public NotePolicy {
        Objects.requireNonNull(extemporaneousAfter, "extemporaneousAfter");
        Objects.requireNonNull(clockSkewTolerance, "clockSkewTolerance");
        Objects.requireNonNull(maxAuthenticationAge, "maxAuthenticationAge");
        if (extemporaneousAfter.isNegative() || extemporaneousAfter.isZero() || clockSkewTolerance.isNegative()
                || maxAuthenticationAge.isNegative() || maxAuthenticationAge.isZero()) {
            throw new IllegalArgumentException("durations must be positive and clockSkewTolerance not negative");
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

    void requireRecentAuthentication(Signer signer, Instant now) {
        if (signer.authenticatedAt().isBefore(now.minus(maxAuthenticationAge))) {
            throw new ClinicalException.RecentAuthenticationRequired();
        }
    }

    boolean isExtemporaneous(Instant occurredAt, Instant recordedAt) {
        return Duration.between(occurredAt, recordedAt).compareTo(extemporaneousAfter) > 0;
    }
}
