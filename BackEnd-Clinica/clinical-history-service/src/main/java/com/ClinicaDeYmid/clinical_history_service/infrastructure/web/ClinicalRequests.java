package com.ClinicaDeYmid.clinical_history_service.infrastructure.web;

import com.ClinicaDeYmid.clinical_history_service.domain.clinician.ClinicalRole;
import com.ClinicaDeYmid.clinical_history_service.domain.encounter.EncounterType;
import com.ClinicaDeYmid.clinical_history_service.domain.note.NoteRestriction;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

final class ClinicalRequests {

    private ClinicalRequests() {
    }

    record OpenEncounter(
            @NotNull(message = "es obligatorio") UUID patientUuid,
            @NotNull(message = "es obligatorio") EncounterType type,
            UUID admissionUuid,
            @Valid CareSettingRequest careSetting) {
    }

    record CareSettingRequest(@NotBlank(message = "es obligatorio") String serviceCode,
                              @NotBlank(message = "es obligatorio") String modality) {
    }

    record Habilitation(@NotBlank(message = "es obligatorio") String serviceCode,
                        @NotBlank(message = "es obligatorio") String modality,
                        @NotNull(message = "es obligatorio") Boolean active) {
    }

    record Draft(@NotNull(message = "es obligatorio") JsonNode content, NoteRestriction restriction, JsonNode updates, Instant occurredAt) {
    }

    record Voiding(String reason) {
    }

    record CareTeamMember(@NotNull(message = "es obligatorio") UUID clinicianUuid, @NotNull(message = "es obligatorio") ClinicalRole role) {
    }

    record EmergencyAccessRequest(String reason) {
    }
}
