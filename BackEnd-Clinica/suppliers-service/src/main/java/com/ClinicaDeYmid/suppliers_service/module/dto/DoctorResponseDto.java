package com.ClinicaDeYmid.suppliers_service.module.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;

public record DoctorResponseDto(
        @Schema(description = "Doctor unique identifier", example = "1")
        Long id,

        @Schema(description = "Provider code for the doctor", example = "12345")
        Integer providerCode,

        @Schema(description = "Doctor's first name", example = "Yamid")
        String name,

        @Schema(description = "Doctor's last name", example = "Ortega")
        String lastName,

        @Schema(description = "Doctor's full name", example = "Yamid Ortega")
        String fullName,

        @Schema(description = "Identification number", example = "123456789")
        String identificationNumber,

        @Schema(description = "Phone number", example = "+573011234567")
        String phoneNumber,

        @Schema(description = "Email address", example = "yamid@example.com")
        String email,

        @Schema(description = "Medical license number", example = "MED-56789")
        String licenseNumber,

        @Schema(description = "Address of the doctor", example = "Calle 123 #45-67")
        String address,

        @Schema(description = "Hourly rate charged by the doctor", example = "100.0")
        Double hourlyRate,

        @Schema(description = "Indicates if the doctor is active", example = "true")
        Boolean active,

        @Schema(description = "Date and time when the doctor was created", example = "2024-07-12T10:15:30")
        LocalDateTime createdAt,

        @Schema(description = "Date and time when the doctor was last updated", example = "2024-07-12T12:00:00")
        LocalDateTime updatedAt,

        @Schema(description = "List of doctor's specialties")
        List<DoctorSpecialtyDto> specialties
) {}
