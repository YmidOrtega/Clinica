package com.ClinicaDeYmid.suppliers_service.module.dto.schedule;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

import java.time.DayOfWeek;
import java.time.LocalTime;

public record DoctorScheduleUpdateDTO(
        @Schema(description = "Día de la semana", example = "MONDAY")
        DayOfWeek dayOfWeek,

        @Schema(description = "Hora de inicio", example = "08:00")
        LocalTime startTime,

        @Schema(description = "Hora de fin", example = "12:00")
        LocalTime endTime,

        @Schema(description = "Estado activo", example = "true")
        Boolean active,

        @Schema(description = "Notas adicionales", example = "Atención matutina")
        @Size(max = 500, message = "Las notas no pueden exceder 500 caracteres")
        String notes
) {
    /**
     * Valida que endTime sea posterior a startTime si ambos están presentes
     */
    public DoctorScheduleUpdateDTO {
        if (endTime != null && startTime != null && !endTime.isAfter(startTime)) {
            throw new IllegalArgumentException("La hora de fin debe ser posterior a la hora de inicio");
        }
    }
}