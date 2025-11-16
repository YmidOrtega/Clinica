package com.ClinicaDeYmid.auth_service.module.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Authentication request DTO")
public record AuthUserDTO(
        @NotBlank(message = "El email es obligatorio")
        @Email(message = "El email debe ser válido")
        @Schema(description = "Email del usuario", example = "user@example.com", requiredMode = Schema.RequiredMode.REQUIRED)
        String email,

        @NotBlank(message = "La contraseña es obligatoria")
        @Schema(description = "Contraseña del usuario", requiredMode = Schema.RequiredMode.REQUIRED)
        String password
) {}