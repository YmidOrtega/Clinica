package com.ClinicaDeYmid.ai_assistant_service.module.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChatRequestDto(
        @Schema(description = "Mensaje del usuario", example = "Hola, necesito crear una atención médica", required = true)
        @NotBlank(message = "El mensaje no puede estar vacío")
        @Size(max = 2000, message = "El mensaje no puede exceder 2000 caracteres")
        String message,

        @Schema(description = "ID de sesión (opcional, se genera automáticamente si no se proporciona)",
                example = "550e8400-e29b-41d4-a716-446655440000")
        String sessionId
) {}