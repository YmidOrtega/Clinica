package com.ClinicaDeYmid.suppliers_service.module.dto.schedule;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.DayOfWeek;
import java.time.LocalTime;

public record DoctorScheduleCreateDTO(
        @Schema(description = "ID del doctor", example = "1", required = true)
        @NotNull(message = "El ID del doctor es obligatorio")
        Long doctorId,

        @Schema(description = "Día de la semana", example = "MONDAY", required = true)
        @NotNull(message = "El día de la semana es obligatorio")
        DayOfWeek dayOfWeek,

        @Schema(description = "Hora de inicio", example = "08:00", required = true)
        @NotNull(message = "La hora de inicio es obligatoria")
        LocalTime startTime,

        @Schema(description = "Hora de fin", example = "12:00", required = true)
        @NotNull(message = "La hora de fin es obligatoria")
        LocalTime endTime,

        @Schema(description = "Notas adicionales", example = "Atención matutina")
        @Size(max = 500, message = "Las notas no pueden exceder 500 caracteres")
        String notes
) {
    /**
     * Valida que endTime sea posterior a startTime
     */
    public DoctorScheduleCreateDTO {
        if (endTime != null && startTime != null && !endTime.isAfter(startTime)) {
            throw new IllegalArgumentException("La hora de fin debe ser posterior a la hora de inicio");
        }
    }
}