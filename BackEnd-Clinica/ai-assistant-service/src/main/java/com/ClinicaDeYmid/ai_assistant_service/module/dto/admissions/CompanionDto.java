package com.ClinicaDeYmid.ai_assistant_service.module.dto.admissions;

import com.fasterxml.jackson.annotation.JsonProperty;

public record CompanionDto(
        @JsonProperty("name")
        String name,

        @JsonProperty("identification_number")
        String identificationNumber,

        @JsonProperty("relationship")
        String relationship,

        @JsonProperty("phone")
        String phone
) {}
