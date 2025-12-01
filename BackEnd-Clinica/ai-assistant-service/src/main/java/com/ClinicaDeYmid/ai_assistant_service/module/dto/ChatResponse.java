package com.ClinicaDeYmid.ai_assistant_service.module.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ChatResponse(
        @JsonProperty("user_id")
        String user_id,

        @JsonProperty("message")
        String message
){
}
