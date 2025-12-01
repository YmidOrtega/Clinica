package com.ClinicaDeYmid.ai_assistant_service.module.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

public record ConversationHistoryDto(
        @Schema(description = "ID de la conversación")
        @JsonProperty("conversation_id")
        Long conversationId,

        @Schema(description = "ID de sesión")
        @JsonProperty("session_id")
        String sessionId,

        @Schema(description = "Nombre del usuario")
        @JsonProperty("username")
        String username,

        @Schema(description = "¿Está activa?")
        @JsonProperty("is_active")
        Boolean isActive,

        @Schema(description = "Fecha de creación")
        @JsonProperty("created_at")
        LocalDateTime createdAt,

        @Schema(description = "Fecha de cierre")
        @JsonProperty("closed_at")
        LocalDateTime closedAt,

        @Schema(description = "Mensajes de la conversación")
        @JsonProperty("messages")
        List<MessageDto> messages
) {}
