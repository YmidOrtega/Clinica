package com.ClinicaDeYmid.ai_assistant_service.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record RequestParams (
        @JsonProperty("user_id")
        String user_id,

        @JsonProperty("message")
        String message
){
}
