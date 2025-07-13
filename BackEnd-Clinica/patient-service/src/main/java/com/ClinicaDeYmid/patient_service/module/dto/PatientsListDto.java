package com.ClinicaDeYmid.patient_service.module.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record PatientsListDto(
        @Schema(description = "Identification number", example = "1234567890")
        String identificationNumber,

        @Schema(description = "First name", example = "Yamid")
        String name,

        @Schema(description = "Last name", example = "Ortega")
        String lastName
) {
}
