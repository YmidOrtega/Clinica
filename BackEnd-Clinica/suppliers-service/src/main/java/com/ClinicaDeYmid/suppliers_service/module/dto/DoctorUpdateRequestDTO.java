package com.ClinicaDeYmid.suppliers_service.module.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record DoctorUpdateRequestDTO(
        @Schema(description = "Doctor's first name", example = "Yamid")
        String name,

        @Schema(description = "Doctor's last name", example = "Ortega")
        String lastName,

        @Schema(description = "Phone number", example = "+573011234567")
        String phoneNumber,

        @Schema(description = "Address of the doctor", example = "Calle 123 #45-67")
        String address,

        @Schema(description = "Hourly rate charged by the doctor", example = "100.0")
        Double hourlyRate,

        @Schema(description = "Indicates if the doctor is active", example = "true")
        Boolean active,

        @Schema(description = "List of sub-specialty IDs", example = "[1,2,3]")
        List<Long> subSpecialtyIds,

        @Schema(description = "List of allowed service type IDs", example = "[1,2]")
        List<Long> allowedServiceTypeIds
) {}
