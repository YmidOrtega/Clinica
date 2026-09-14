package com.ClinicaDeYmid.clinical_history_service.application.access;

import com.ClinicaDeYmid.clinical_history_service.domain.access.AccessAction;
import com.ClinicaDeYmid.clinical_history_service.domain.access.AccessBasis;
import com.ClinicaDeYmid.clinical_history_service.domain.clinician.Clinician;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record AccessEvent(
        UUID patientUuid,
        Actor actor,
        AccessAction action,
        UUID resourceId,
        Outcome outcome,
        AccessBasis basis,
        boolean restrictedContent,
        String emergencyReason,
        String exportReason,
        Instant occurredAt) {

    public record Actor(UUID uuid, String role) {

        public Actor {
            Objects.requireNonNull(uuid, "uuid");
            Objects.requireNonNull(role, "role");
        }

        public static Actor of(Clinician clinician) {
            return new Actor(clinician.uuid(), clinician.role().name());
        }
    }

    public AccessEvent(UUID patientUuid, Clinician actor, AccessAction action, UUID resourceId, Outcome outcome, AccessBasis basis,
                       boolean restrictedContent, String emergencyReason, Instant occurredAt) {
        this(patientUuid, Actor.of(actor), action, resourceId, outcome, basis, restrictedContent, emergencyReason, null, occurredAt);
    }

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
