package com.ClinicaDeYmid.suppliers_service.module.dto.search;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

public record DoctorAvailabilityStatsDTO(
        @Schema(description = "ID del doctor", example = "1")
        Long doctorId,

        @Schema(description = "Nombre completo del doctor", example = "Dr. Juan Pérez")
        String doctorFullName,

        @Schema(description = "Total de horarios configurados", example = "5")
        Integer totalSchedules,

        @Schema(description = "Horarios activos", example = "4")
        Integer activeSchedules,

        @Schema(description = "Ausencias futuras", example = "2")
        Integer upcomingUnavailabilities,

        @Schema(description = "¿Está actualmente disponible?", example = "true")
        Boolean currentlyAvailable,

        @Schema(description = "Próxima ausencia programada")
        LocalDate nextUnavailabilityDate
) {}
