package com.ClinicaDeYmid.suppliers_service.module.dto.search;

import io.swagger.v3.oas.annotations.media.Schema;

public record DoctorSearchFiltersDTO(
        @Schema(description = "ID de especialidad", example = "1")
        Long specialtyId,

        @Schema(description = "ID de subespecialidad", example = "10")
        Long subSpecialtyId,

        @Schema(description = "Término de búsqueda (nombre o apellido)", example = "Juan")
        String searchTerm,

        @Schema(description = "Solo doctores activos", example = "true")
        Boolean activeOnly,

        @Schema(description = "Solo doctores con horarios configurados", example = "true")
        Boolean withSchedulesOnly
) {
    /**
     * Constructor por defecto con valores predeterminados
     */
    public DoctorSearchFiltersDTO {
        if (activeOnly == null) {
            activeOnly = true;
        }
        if (withSchedulesOnly == null) {
            withSchedulesOnly = false;
        }
    }
}