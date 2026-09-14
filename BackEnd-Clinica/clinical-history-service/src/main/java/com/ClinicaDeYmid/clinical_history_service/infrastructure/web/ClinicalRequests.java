package com.ClinicaDeYmid.clinical_history_service.infrastructure.web;

import com.ClinicaDeYmid.clinical_history_service.domain.encounter.EncounterType;
import com.ClinicaDeYmid.clinical_history_service.domain.note.NoteRestriction;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

final class ClinicalRequests {

    private ClinicalRequests() {
    }

    record OpenEncounter(
            @NotNull(message = "es obligatorio") UUID patientUuid,
            @NotNull(message = "es obligatorio") EncounterType type,
            String admissionId) {
    }

    record Draft(@NotNull(message = "es obligatorio") JsonNode content, NoteRestriction restriction, Instant occurredAt) {
    }

    record Voiding(String reason) {
    }
}
