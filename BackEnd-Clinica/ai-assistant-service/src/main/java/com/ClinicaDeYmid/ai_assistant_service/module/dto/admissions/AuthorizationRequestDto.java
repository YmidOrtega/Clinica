package com.ClinicaDeYmid.ai_assistant_service.module.dto.admissions;

import com.fasterxml.jackson.annotation.JsonProperty;

public record AuthorizationRequestDto(
        @JsonProperty("authorization_number")
        String authorizationNumber,

        @JsonProperty("authorization_date")
        String authorizationDate,

        @JsonProperty("authorizing_entity")
        String authorizingEntity,

        @JsonProperty("authorized_by")
        String authorizedBy,

        @JsonProperty("services")
        String services,

        @JsonProperty("quantity")
        Integer quantity,

        @JsonProperty("observations")
        String observations
) {}
