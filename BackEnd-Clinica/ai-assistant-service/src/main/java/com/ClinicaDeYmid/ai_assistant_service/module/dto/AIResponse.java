package com.ClinicaDeYmid.ai_assistant_service.module.dto;

public record AIResponse(
        String answer,
        SqlResponse sqlResponse
) {
}
