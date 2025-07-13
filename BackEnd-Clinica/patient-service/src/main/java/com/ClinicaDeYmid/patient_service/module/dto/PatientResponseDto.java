package com.ClinicaDeYmid.patient_service.module.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

public record PatientResponseDto(
        @Schema(description = "Unique identifier for the patient", example = "123e4567-e89b-12d3-a456-426614174000")
        String uuid,

        @Schema(description = "First name of the patient", example = "Yamid")
        String name,

        @Schema(description = "Last name of the patient", example = "Ortega")
        String lastName,

        @Schema(description = "Identification number", example = "1234567890")
        String identificationNumber,

        @Schema(description = "Email address", example = "yamid@example.com")
        String email,

        @Schema(description = "Date and time when the patient was created", example = "2024-07-13T10:20:00")
        LocalDateTime createdAt,

        @Schema(description = "Client information")
        GetClientDto clientInfo
){
}
