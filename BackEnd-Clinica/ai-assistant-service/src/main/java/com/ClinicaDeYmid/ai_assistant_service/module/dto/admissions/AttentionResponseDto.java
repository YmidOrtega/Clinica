package com.ClinicaDeYmid.ai_assistant_service.module.dto.admissions;

import com.fasterxml.jackson.annotation.JsonProperty;

public record AttentionResponseDto(
        @JsonProperty("id")
        Long id,

        @JsonProperty("patient_id")
        Long patientId,

        @JsonProperty("doctor_id")
        Long doctorId,

        @JsonProperty("status")
        String status,

        @JsonProperty("attention_number")
        String attentionNumber,

        @JsonProperty("cause")
        String cause,

        @JsonProperty("triage_level")
        String triageLevel,

        @JsonProperty("entry_method")
        String entryMethod,

        @JsonProperty("active")
        Boolean active,

        @JsonProperty("invoiced")
        Boolean invoiced,

        @JsonProperty("created_at")
        String createdAt
) {}
