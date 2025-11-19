package com.ClinicaDeYmid.suppliers_service.module.dto.schedule;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;

public record DoctorScheduleResponseDTO(
        @Schema(description = "ID del horario", example = "1")
        Long id,

        @Schema(description = "ID del doctor", example = "1")
        Long doctorId,

        @Schema(description = "Nombre completo del doctor", example = "Dr. Juan Pérez")
        String doctorFullName,

        @Schema(description = "Día de la semana", example = "MONDAY")
        DayOfWeek dayOfWeek,

        @Schema(description = "Día de la semana en español", example = "Lunes")
        String dayOfWeekSpanish,

        @Schema(description = "Hora de inicio", example = "08:00")
        LocalTime startTime,

        @Schema(description = "Hora de fin", example = "12:00")
        LocalTime endTime,

        @Schema(description = "Estado activo", example = "true")
        Boolean active,

        @Schema(description = "Notas adicionales", example = "Atención matutina")
        String notes,

        @Schema(description = "Fecha de creación", example = "2025-01-15T10:30:00")
        LocalDateTime createdAt,

        @Schema(description = "Fecha de última actualización", example = "2025-01-15T10:30:00")
        LocalDateTime updatedAt
) {
    /**
     * Constructor que calcula el día en español
     */
    public DoctorScheduleResponseDTO(
            Long id,
            Long doctorId,
            String doctorFullName,
            DayOfWeek dayOfWeek,
            LocalTime startTime,
            LocalTime endTime,
            Boolean active,
            String notes,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
        this(id, doctorId, doctorFullName, dayOfWeek, getDayOfWeekInSpanish(dayOfWeek),
                startTime, endTime, active, notes, createdAt, updatedAt);
    }

    /**
     * Convierte el día de la semana a español
     */
    private static String getDayOfWeekInSpanish(DayOfWeek dayOfWeek) {
        return switch (dayOfWeek) {
            case MONDAY -> "Lunes";
            case TUESDAY -> "Martes";
            case WEDNESDAY -> "Miércoles";
            case THURSDAY -> "Jueves";
            case FRIDAY -> "Viernes";
            case SATURDAY -> "Sábado";
            case SUNDAY -> "Domingo";
        };
    }
}