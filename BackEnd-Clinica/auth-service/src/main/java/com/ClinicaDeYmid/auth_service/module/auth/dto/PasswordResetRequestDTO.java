package com.ClinicaDeYmid.auth_service.module.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Request para iniciar reseteo de contraseña")
public record PasswordResetRequestDTO(
        @NotBlank(message = "El email es requerido")
        @Email(message = "Email inválido")
        @Schema(description = "Email del usuario", example = "user@example.com")
        String email
) {}