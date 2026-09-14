package com.ClinicaDeYmid.clinical_history_service.application.access;

import com.ClinicaDeYmid.clinical_history_service.domain.access.AccessAction;
import com.ClinicaDeYmid.clinical_history_service.domain.access.AccessBasis;
import com.ClinicaDeYmid.clinical_history_service.domain.clinician.Clinician;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record AccessEvent(
        UUID patientUuid,
        Clinician actor,
        AccessAction action,
        UUID resourceId,
        Outcome outcome,
        AccessBasis basis,
        boolean restrictedContent,
        String emergencyReason,
        Instant occurredAt) {

    public enum Outcome {
        GRANTED,
        DENIED
    }

    public AccessEvent {
        Objects.requireNonNull(patientUuid, "patientUuid");
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(resourceId, "resourceId");
        Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(occurredAt, "occurredAt");
        if ((outcome == Outcome.GRANTED) != (basis != null)) {
            throw new IllegalArgumentException("Granted access needs a basis and denied access must not have one");
        }
    }
}
