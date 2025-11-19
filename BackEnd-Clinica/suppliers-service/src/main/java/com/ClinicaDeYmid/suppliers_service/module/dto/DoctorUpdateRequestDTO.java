package com.ClinicaDeYmid.suppliers_service.module.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

import java.util.List;

public record DoctorUpdateRequestDTO(
        @Schema(description = "Provider code for the doctor", example = "12345")
        @Min(value = 1, message = "El código de proveedor debe ser mayor a 0")
        @Max(value = 999999, message = "El código de proveedor no puede exceder 999999")
        Integer providerCode,

        @Schema(description = "Doctor's first name", example = "Yamid")
        @Size(min = 2, max = 100, message = "El nombre debe tener entre 2 y 100 caracteres")
        @Pattern(regexp = "^[a-zA-ZáéíóúÁÉÍÓÚñÑ' ]+$",
                message = "El nombre solo puede contener letras, espacios y tildes")
        String name,

        @Schema(description = "Doctor's last name", example = "Ortega")
        @Size(min = 2, max = 100, message = "El apellido debe tener entre 2 y 100 caracteres")
        @Pattern(regexp = "^[a-zA-ZáéíóúÁÉÍÓÚñÑ' ]+$",
                message = "El apellido solo puede contener letras, espacios y tildes")
        String lastName,

        @Schema(description = "Identification number", example = "123456789")
        @Size(min = 5, max = 20, message = "El número de identificación debe tener entre 5 y 20 caracteres")
        @Pattern(regexp = "^[A-Za-z0-9-]+$",
                message = "El número de identificación solo puede contener letras, números y guiones")
        String identificationNumber,

        @Schema(description = "List of specialty IDs", example = "[1,2]")
        @Size(min = 1, message = "Debe especificar al menos una especialidad si actualiza especialidades")
        List<@NotNull(message = "Los IDs de especialidades no pueden ser nulos")
        @Positive(message = "Los IDs de especialidades deben ser positivos")
                Long> specialtyIds,

        @Schema(description = "List of sub-specialty IDs", example = "[10,12]")
        List<@NotNull(message = "Los IDs de subespecialidades no pueden ser nulos")
        @Positive(message = "Los IDs de subespecialidades deben ser positivos")
                Long> subSpecialtyIds,

        @Schema(description = "Phone number", example = "+573011234567")
        @Pattern(regexp = "^[+]?[0-9\\s-]{7,20}$",
                message = "El teléfono debe contener entre 7 y 20 dígitos, puede incluir '+', espacios y guiones")
        String phoneNumber,

        @Schema(description = "Email address", example = "yamid@example.com")
        @Email(message = "El email debe tener un formato válido")
        @Size(max = 150, message = "El email no puede exceder 150 caracteres")
        String email,

        @Schema(description = "Medical license number", example = "MED-56789")
        @Pattern(regexp = "^[A-Z]{2,4}-[0-9]{4,10}$",
                message = "La licencia médica debe tener formato: AA-1234 o AAA-12345 (2-4 letras mayúsculas, guion, 4-10 números)")
        @Size(max = 50, message = "La licencia médica no puede exceder 50 caracteres")
        String licenseNumber,

        @Schema(description = "Address of the doctor", example = "Calle 123 #45-67")
        @Size(max = 200, message = "La dirección no puede exceder 200 caracteres")
        String address,

        @Schema(description = "Hourly rate charged by the doctor", example = "100.0")
        @Min(value = 0, message = "La tarifa por hora no puede ser negativa")
        @Max(value = 1000000, message = "La tarifa por hora no puede exceder 1,000,000")
        @Digits(integer = 7, fraction = 2, message = "La tarifa debe tener máximo 7 dígitos enteros y 2 decimales")
        Double hourlyRate,

        @Schema(description = "Active status", example = "true")
        Boolean active
) {}