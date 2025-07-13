package com.ClinicaDeYmid.auth_service.module.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import java.time.LocalDate;

public record UserUpdateDTO(
        @Schema(description = "Nombre de usuario", example = "yamid.ortega")
        @Size(min = 3, max = 50, message = "El username debe tener entre 3 y 50 caracteres")
        @Pattern(regexp = "^[a-zA-Z0-9._-]+$", message = "El username solo puede contener letras, números, puntos, guiones y guiones bajos")
        String username,

        @Schema(description = "Fecha de nacimiento", example = "1992-06-12")
        @Past(message = "La fecha de nacimiento debe ser en el pasado")
        LocalDate birthDate,

        @Schema(description = "Correo electrónico", example = "usuario@clinica.com")
        @Email(message = "El formato del email no es válido")
        @Size(max = 100, message = "El email no puede exceder 100 caracteres")
        String email,

        @Schema(description = "Número de teléfono", example = "+573001234567")
        @Size(min = 7, max = 20, message = "El número de teléfono debe tener entre 7 y 20 caracteres")
        @Pattern(
                regexp = "^[+]?[0-9\\s\\-()]{7,20}$",
                message = "Formato de teléfono inválido"
        )
        String phoneNumber,

        @Schema(description = "ID del rol a asignar", example = "2")
        @Positive(message = "El ID del rol debe ser un número positivo")
        Long roleId
) {}
