package com.ClinicaDeYmid.suppliers_service.module.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import java.util.List;

public record DoctorCreateRequestDTO(
        @Schema(description = "Provider code for the doctor", example = "12345", required = true)
        @NotNull Integer providerCode,

        @Schema(description = "Doctor's first name", example = "Yamid", required = true)
        @NotBlank String name,

        @Schema(description = "Doctor's last name", example = "Ortega", required = true)
        @NotBlank String lastName,

        @Schema(description = "Identification number", example = "123456789", required = true)
        @NotBlank String identificationNumber,

        @Schema(description = "List of specialty IDs", example = "[1,2]", required = true)
        @NotEmpty List<Long> specialtyIds,

        @Schema(description = "List of sub-specialty IDs", example = "[10,12]", required = false)
        List<Long> subSpecialtyIds,

        @Schema(description = "Phone number", example = "+573011234567", required = true)
        @NotBlank String phoneNumber,

        @Schema(description = "Email address", example = "yamid@example.com", required = true)
        @Email @NotBlank String email,

        @Schema(description = "Medical license number", example = "MED-56789", required = true)
        @NotBlank String licenseNumber,

        @Schema(description = "Address of the doctor", example = "Calle 123 #45-67", required = false)
        String address,

        @Schema(description = "Hourly rate charged by the doctor", example = "100.0", required = false)
        Double hourlyRate
) {}
