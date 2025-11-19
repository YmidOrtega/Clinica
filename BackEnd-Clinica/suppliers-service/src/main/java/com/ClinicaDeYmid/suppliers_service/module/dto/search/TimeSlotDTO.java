package com.ClinicaDeYmid.suppliers_service.module.dto.search;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.DayOfWeek;
import java.time.LocalTime;

public record TimeSlotDTO(
        @Schema(description = "Hora de inicio", example = "08:00")
        LocalTime startTime,

        @Schema(description = "Hora de fin", example = "12:00")
        LocalTime endTime,

        @Schema(description = "Día de la semana", example = "MONDAY")
        DayOfWeek dayOfWeek,

        @Schema(description = "Día en español", example = "Lunes")
        String dayOfWeekSpanish
) {}
