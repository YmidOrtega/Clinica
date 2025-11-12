package com.ClinicaDeYmid.auth_service.module.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Información sobre políticas de contraseña")
public record PasswordPolicyInfoDTO(
        @Schema(description = "Longitud mínima", example = "8")
        int minLength,

        @Schema(description = "Requiere mayúsculas", example = "true")
        boolean requireUppercase,

        @Schema(description = "Requiere minúsculas", example = "true")
        boolean requireLowercase,

        @Schema(description = "Requiere dígitos", example = "true")
        boolean requireDigit,

        @Schema(description = "Requiere caracteres especiales", example = "true")
        boolean requireSpecialChar,

        @Schema(description = "Número de contraseñas que se mantienen en historial", example = "5")
        int passwordHistoryCount,

        @Schema(description = "Días antes de que expire la contraseña", example = "90")
        int passwordExpirationDays
) {}