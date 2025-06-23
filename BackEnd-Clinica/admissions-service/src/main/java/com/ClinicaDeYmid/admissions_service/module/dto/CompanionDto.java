package com.ClinicaDeYmid.admissions_service.module.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CompanionDto(
        @Size(max = 100, message = "El nombre completo no puede exceder 100 caracteres")
        @Pattern(regexp = "^[a-zA-ZÀ-ÿ\\s]*$", message = "El nombre solo puede contener letras y espacios")
        String fullName,

        @Size(max = 15, message = "El número de teléfono no puede exceder 15 caracteres")
        @Pattern(regexp = "^[+]?[0-9\\s()-]*$", message = "Formato de teléfono no válido")
        String phoneNumber,

        @Size(max = 50, message = "La relación no puede exceder 50 caracteres")
        @Pattern(regexp = "^[a-zA-ZÀ-ÿ\\s]*$", message = "La relación solo puede contener letras y espacios")
        String relationship
) {}
