package com.ClinicaDeYmid.auth_service.module.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

import java.time.LocalDate;

@Schema(description = "Request DTO for creating a new user")
public record UserRequestDTO(
        @NotBlank(message = "El username es obligatorio")
        @Size(min = 3, max = 50, message = "El username debe tener entre 3 y 50 caracteres")
        @Schema(description = "Username del usuario", example = "john_doe", requiredMode = Schema.RequiredMode.REQUIRED)
        String username,

        @NotNull(message = "La fecha de nacimiento es obligatoria")
        @Past(message = "La fecha de nacimiento debe ser en el pasado")
        @Schema(description = "Fecha de nacimiento del usuario", example = "1990-01-01", requiredMode = Schema.RequiredMode.REQUIRED)
        LocalDate birthDate,

        @NotBlank(message = "El email es obligatorio")
        @Email(message = "El email debe ser válido")
        @Schema(description = "Email del usuario", example = "john@example.com", requiredMode = Schema.RequiredMode.REQUIRED)
        String email,

        @NotBlank(message = "La contraseña es obligatoria")
        @Size(min = 8, message = "La contraseña debe tener al menos 8 caracteres")
        @Schema(description = "Contraseña del usuario", example = "SecurePass123!", requiredMode = Schema.RequiredMode.REQUIRED)
        String password,

        @Pattern(regexp = "^\\+?[1-9]\\d{1,14}$", message = "El número de teléfono debe ser válido")
        @Schema(description = "Número de teléfono del usuario", example = "+1234567890")
        String phoneNumber,

        @NotNull(message = "El role ID es obligatorio")
        @Positive(message = "El role ID debe ser un número positivo")
        @Schema(description = "ID del rol del usuario", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
        Long roleId
) {}