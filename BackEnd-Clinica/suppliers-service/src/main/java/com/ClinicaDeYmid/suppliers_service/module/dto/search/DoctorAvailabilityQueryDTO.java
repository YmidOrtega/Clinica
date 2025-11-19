package com.ClinicaDeYmid.suppliers_service.module.dto.search;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;

public record DoctorAvailabilityQueryDTO(
        @Schema(description = "Fecha de la consulta", example = "2025-12-20", required = true)
        @NotNull(message = "La fecha es obligatoria")
        LocalDate date,

        @Schema(description = "Hora de la consulta", example = "10:00", required = true)
        @NotNull(message = "La hora es obligatoria")
        LocalTime time,

        @Schema(description = "ID de especialidad (opcional)", example = "1")
        Long specialtyId,

        @Schema(description = "ID de subespecialidad (opcional)", example = "10")
        Long subSpecialtyId
) {
    /**
     * Obtiene el día de la semana de la fecha
     */
    public DayOfWeek getDayOfWeek() {
        return date.getDayOfWeek();
    }
}
