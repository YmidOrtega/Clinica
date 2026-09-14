package com.ClinicaDeYmid.clinical_history_service.infrastructure.audit;

import com.ClinicaDeYmid.clinical_history_service.application.access.AccessEvent;

import java.time.Instant;
import java.util.UUID;

record AccessAuditMessage(
        UUID eventId,
        String type,
        int schemaVersion,
        Instant occurredAt,
        String traceId,
        UUID patientUuid,
        Actor actor,
        String action,
        Resource resource,
        String outcome,
        String basis,
        boolean restrictedContent,
        String emergencyReason,
        String exportReason) {

    static final int SCHEMA_VERSION = 1;

    record Actor(UUID uuid, String role) {
    }

    record Resource(UUID id) {
    }

    static AccessAuditMessage of(AccessEvent event, UUID eventId, String traceId) {
        return new AccessAuditMessage(eventId, typeOf(event.outcome()), SCHEMA_VERSION, event.occurredAt(), traceId, event.patientUuid(),
                new Actor(event.actor().uuid(), event.actor().role()), event.action().name(), new Resource(event.resourceId()),
                event.outcome().name(), event.basis() == null ? null : event.basis().name(), event.restrictedContent(),
                event.emergencyReason(), event.exportReason());
    }

    static String typeOf(AccessEvent.Outcome outcome) {
        return switch (outcome) {
            case GRANTED -> "ClinicalRecordAccessed";
            case DENIED -> "ClinicalRecordAccessDenied";
        };
    }
}
