package com.ClinicaDeYmid.auth_service.module.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "Información de sesión activa")
public record ActiveSessionDTO(
        @Schema(description = "ID de la sesión")
        Long id,

        @Schema(description = "Dirección IP", example = "192.168.1.1")
        String ipAddress,

        @Schema(description = "User agent del navegador")
        String userAgent,

        @Schema(description = "Fecha de creación")
        LocalDateTime createdAt,

        @Schema(description = "Fecha de expiración")
        LocalDateTime expiresAt,

        @Schema(description = "¿Es la sesión actual?")
        boolean isCurrent
) {}