package com.ClinicaDeYmid.ai_assistant_service.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ChatResponse(
        @JsonProperty("response")
        String response

) {
}
