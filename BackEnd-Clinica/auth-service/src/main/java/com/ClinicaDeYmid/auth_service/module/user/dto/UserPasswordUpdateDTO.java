package com.ClinicaDeYmid.auth_service.module.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Request DTO for updating user password")
public record UserPasswordUpdateDTO(
        @NotBlank(message = "La contraseña actual es obligatoria")
        @Schema(description = "Contraseña actual del usuario", requiredMode = Schema.RequiredMode.REQUIRED)
        String currentPassword,

        @NotBlank(message = "La nueva contraseña es obligatoria")
        @Size(min = 8, message = "La nueva contraseña debe tener al menos 8 caracteres")
        @Schema(description = "Nueva contraseña del usuario", example = "NewSecurePass123!", requiredMode = Schema.RequiredMode.REQUIRED)
        String newPassword,

        @NotBlank(message = "La confirmación de la contraseña es obligatoria")
        @Schema(description = "Confirmación de la nueva contraseña", requiredMode = Schema.RequiredMode.REQUIRED)
        String confirmPassword
) {
    public boolean passwordsMatch() {
        return newPassword != null && newPassword.equals(confirmPassword);
    }
}