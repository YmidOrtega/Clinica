package com.ClinicaDeYmid.ai_assistant_service.dto;

public record AIResponse(
        String answer,
        SqlResponse sqlResponse
) {
}
