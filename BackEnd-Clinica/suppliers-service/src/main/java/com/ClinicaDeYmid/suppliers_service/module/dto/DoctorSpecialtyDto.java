package com.ClinicaDeYmid.suppliers_service.module.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record DoctorSpecialtyDto(
        @Schema(description = "Specialty name", example = "Cardiology")
        String name,

        @Schema(description = "Code of the specialty", example = "101")
        int codeSpeciality,

        @Schema(description = "List of sub-specialties details")
        List<SubSpecialtyDetailsDto> subSpecialtiesDetails
) {}
