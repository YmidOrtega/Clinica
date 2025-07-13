package com.ClinicaDeYmid.auth_service.module.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

public record UserPasswordUpdateDTO(
        @Schema(description = "Contraseña actual del usuario", example = "PasswordActual$2024", required = true)
        @NotBlank(message = "La contraseña actual es obligatoria")
        String currentPassword,

        @Schema(
                description = "Nueva contraseña. Debe tener al menos 1 minúscula, 1 mayúscula, 1 número y 1 carácter especial",
                example = "NuevoPassword$2024",
                required = true
        )
        @NotBlank(message = "La nueva contraseña es obligatoria")
        @Size(min = 8, max = 100, message = "La contraseña debe tener entre 8 y 100 caracteres")
        @Pattern(
                regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]{8,}$",
                message = "La contraseña debe contener al menos: 1 minúscula, 1 mayúscula, 1 número y 1 carácter especial"
        )
        String newPassword,

        @Schema(description = "Confirmación de la nueva contraseña", example = "NuevoPassword$2024", required = true)
        @NotBlank(message = "La confirmación de contraseña es obligatoria")
        String confirmPassword
) {
    public boolean isPasswordConfirmed() {
        return newPassword != null && newPassword.equals(confirmPassword);
    }
}
