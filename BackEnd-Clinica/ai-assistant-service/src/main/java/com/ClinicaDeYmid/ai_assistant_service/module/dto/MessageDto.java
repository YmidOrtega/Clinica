package com.ClinicaDeYmid.ai_assistant_service.module.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

public record MessageDto(
        @Schema(description = "ID del mensaje")
        @JsonProperty("message_id")
        Long messageId,

        @Schema(description = "Rol del emisor", example = "USER")
        @JsonProperty("role")
        String role,

        @Schema(description = "Contenido del mensaje")
        @JsonProperty("content")
        String content,

        @Schema(description = "Fecha de creación")
        @JsonProperty("created_at")
        LocalDateTime createdAt,

        @Schema(description = "Metadatos adicionales")
        @JsonProperty("metadata")
        String metadata
) {}
