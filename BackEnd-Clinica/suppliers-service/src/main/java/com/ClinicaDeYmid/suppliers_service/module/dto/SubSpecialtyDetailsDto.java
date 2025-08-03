package com.ClinicaDeYmid.suppliers_service.module.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record SubSpecialtyDetailsDto(
        @Schema(description = "ID of the sub-specialty", example = "10")
        Long id,

        @Schema(description = "Name of the sub-specialty", example = "Cardiología Pediátrica")
        String name,

        @Schema(description = "Code of the sub-specialty", example = "201")
        int codeSubSpecialty
) {}
