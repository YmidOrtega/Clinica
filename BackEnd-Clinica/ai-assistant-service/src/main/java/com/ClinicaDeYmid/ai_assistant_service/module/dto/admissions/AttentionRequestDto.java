package com.ClinicaDeYmid.ai_assistant_service.module.dto.admissions;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record AttentionRequestDto(
        @JsonProperty("patient_id")
        Long patientId,

        @JsonProperty("doctor_id")
        Long doctorId,

        @JsonProperty("configuration_service_id")
        Long configurationServiceId,

        @JsonProperty("status")
        String status,

        @JsonProperty("cause")
        String cause,

        @JsonProperty("health_providers")
        List<HealthProviderRequestDto> healthProviders,

        @JsonProperty("diagnostic_codes")
        List<String> diagnosticCodes,

        @JsonProperty("triage_level")
        String triageLevel,

        @JsonProperty("entry_method")
        String entryMethod,

        @JsonProperty("companion")
        CompanionDto companion,

        @JsonProperty("observations")
        String observations,

        @JsonProperty("authorizations")
        List<AuthorizationRequestDto> authorizations,

        @JsonProperty("user_id")
        Long userId
) {}
