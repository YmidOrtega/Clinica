package com.ClinicaDeYmid.suppliers_service.module.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record SpecialtyDetailsDto(
        @Schema(description = "ID of the specialty", example = "1")
        Long id,

        @Schema(description = "Name of the specialty", example = "Cardiología")
        String name,

        @Schema(description = "Code of the specialty", example = "101")
        int codeSpeciality,

        @Schema(description = "Sub-specialties that the doctor has in this specialty")
        List<SubSpecialtyDetailsDto> subSpecialties
) {}
