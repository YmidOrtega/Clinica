package com.ClinicaDeYmid.ai_assistant_service.module.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

public record ChatResponseDto(
        @Schema(description = "ID de la sesión de conversación")
        @JsonProperty("session_id")
        String sessionId,

        @Schema(description = "Mensaje de respuesta del asistente")
        @JsonProperty("message")
        String message,

        @Schema(description = "Nombre del usuario")
        @JsonProperty("username")
        String username,

        @Schema(description = "Intent detectado (opcional)")
        @JsonProperty("intent")
        String intent,

        @Schema(description = "Acción ejecutada (opcional)")
        @JsonProperty("action")
        String action,

        @Schema(description = "ID de la atención creada (si aplica)")
        @JsonProperty("attention_id")
        Long attentionId
) {}
