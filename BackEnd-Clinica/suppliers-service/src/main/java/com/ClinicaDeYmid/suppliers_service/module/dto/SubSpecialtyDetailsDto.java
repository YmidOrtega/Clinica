package com.ClinicaDeYmid.suppliers_service.module.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record SubSpecialtyDetailsDto(
        @Schema(description = "Sub-specialty name", example = "Pediatric Cardiology")
        String name,

        @Schema(description = "Sub-specialty code", example = "201")
        int codeSubSpecialty
) {}
