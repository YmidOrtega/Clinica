package com.ClinicaDeYmid.admissions_service.module.dto.attention;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record ConfigurationServiceResponseDto(
        @Schema(description = "ID del servicio de configuración", example = "20", required = true)
        @NotNull Long id,

        @Schema(description = "Tipo de servicio", example = "Consulta Externa")
        String serviceTypeName,

        @Schema(description = "Tipo de atención", example = "Urgencia")
        String careTypeName,

        @Schema(description = "Nombre de la sede", example = "Sede Principal")
        String locationName,

        @Schema(description = "¿Está activo?", example = "true")
        boolean active
) {}