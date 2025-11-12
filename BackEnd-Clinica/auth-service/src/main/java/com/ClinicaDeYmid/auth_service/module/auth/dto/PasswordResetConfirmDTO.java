package com.ClinicaDeYmid.auth_service.module.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Request para confirmar reseteo de contraseña")
public record PasswordResetConfirmDTO(
        @NotBlank(message = "El token es requerido")
        @Schema(description = "Token de reseteo recibido por email")
        String token,

        @NotBlank(message = "La nueva contraseña es requerida")
        @Size(min = 8, message = "La contraseña debe tener al menos 8 caracteres")
        @Schema(description = "Nueva contraseña", example = "NewSecure123!")
        String newPassword,

        @NotBlank(message = "La confirmación de contraseña es requerida")
        @Schema(description = "Confirmación de nueva contraseña", example = "NewSecure123!")
        String confirmPassword
) {
    public boolean isPasswordConfirmed() {
        return newPassword != null && newPassword.equals(confirmPassword);
    }
}