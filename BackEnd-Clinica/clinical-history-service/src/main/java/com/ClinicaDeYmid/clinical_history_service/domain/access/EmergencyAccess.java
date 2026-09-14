package com.ClinicaDeYmid.clinical_history_service.domain.access;

import com.ClinicaDeYmid.clinical_history_service.domain.ClinicalException;
import com.ClinicaDeYmid.clinical_history_service.domain.ClinicalText;
import com.ClinicaDeYmid.clinical_history_service.domain.clinician.Clinician;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record EmergencyAccess(UUID id, UUID patientUuid, Clinician clinician, String reason, Instant grantedAt, Instant expiresAt) {

    public EmergencyAccess {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(patientUuid, "patientUuid");
        Objects.requireNonNull(clinician, "clinician");
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(grantedAt, "grantedAt");
        Objects.requireNonNull(expiresAt, "expiresAt");
        if (!expiresAt.isAfter(grantedAt)) {
            throw new IllegalArgumentException("expiresAt must be after grantedAt");
        }
    }

    public static EmergencyAccess grant(UUID patientUuid, Clinician clinician, String reason, Duration duration, Clock clock) {
        String justification = ClinicalText.required(reason, "reason", ClinicalText.SHORT);
        if (justification.length() < 10) {
            throw new ClinicalException.InvalidData("reason",
                    "debe explicar la emergencia en al menos 10 caracteres");
        }
        Instant now = Instant.now(clock);
        return new EmergencyAccess(UUID.randomUUID(), patientUuid, clinician, justification, now, now.plus(duration));
    }

    public boolean isActiveAt(Instant now) {
        return now.isBefore(expiresAt);
    }
}
