package com.ClinicaDeYmid.auth_service.module.user.dto;

import jakarta.validation.constraints.*;

import java.time.LocalDate;
import java.util.Set;

public record UserRequestDTO(
        @NotBlank(message = "El username es obligatorio")
        @Size(min = 3, max = 50, message = "El username debe tener entre 3 y 50 caracteres")
        @Pattern(regexp = "^[a-zA-Z0-9._-]+$", message = "El username solo puede contener letras, números, puntos, guiones y guiones bajos")
        String username,

        @NotNull(message = "La fecha de nacimiento es obligatoria")
        @Past(message = "La fecha de nacimiento debe ser en el pasado")
        LocalDate birthDate,

        @NotBlank(message = "El email es obligatorio")
        @Email(message = "El formato del email no es válido")
        @Size(max = 100, message = "El email no puede exceder 100 caracteres")
        String email,

        @NotBlank(message = "La contraseña es obligatoria")
        @Size(min = 8, max = 100, message = "La contraseña debe tener entre 8 y 100 caracteres")
        @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]{8,}$",
                message = "La contraseña debe contener al menos: 1 minúscula, 1 mayúscula, 1 número y 1 carácter especial")
        String password,

        @NotBlank(message = "El número de teléfono es obligatorio")
        @Size(min = 7, max = 20, message = "El número de teléfono debe tener entre 7 y 20 caracteres")
        @Pattern(regexp = "^[+]?[0-9\\s\\-()]{7,20}$", message = "Formato de teléfono inválido")
        String phoneNumber,

        @NotNull(message = "El ID del rol es obligatorio")
        @Positive(message = "El ID del rol debe ser un número positivo")
        Long roleId
) {}