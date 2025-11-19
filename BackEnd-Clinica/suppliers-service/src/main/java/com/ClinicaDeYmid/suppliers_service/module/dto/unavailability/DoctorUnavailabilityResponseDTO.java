package com.ClinicaDeYmid.suppliers_service.module.dto.unavailability;

import com.ClinicaDeYmid.suppliers_service.module.enums.UnavailabilityType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

public record DoctorUnavailabilityResponseDTO(
        @Schema(description = "ID de la ausencia", example = "1")
        Long id,

        @Schema(description = "ID del doctor", example = "1")
        Long doctorId,

        @Schema(description = "Nombre completo del doctor", example = "Dr. Juan Pérez")
        String doctorFullName,

        @Schema(description = "Tipo de ausencia", example = "VACATION")
        UnavailabilityType type,

        @Schema(description = "Nombre del tipo en español", example = "Vacaciones")
        String typeDisplayName,

        @Schema(description = "Fecha de inicio", example = "2025-12-20")
        LocalDate startDate,

        @Schema(description = "Fecha de fin", example = "2025-12-31")
        LocalDate endDate,

        @Schema(description = "Duración en días", example = "12")
        Long durationDays,

        @Schema(description = "Razón de la ausencia", example = "Vacaciones de fin de año")
        String reason,

        @Schema(description = "Estado de aprobación", example = "true")
        Boolean approved,

        @Schema(description = "Usuario que aprobó", example = "admin")
        String approvedBy,

        @Schema(description = "Fecha de aprobación", example = "2025-12-15")
        LocalDate approvedAt
) {
    /**
     * Calcula la duración en días entre las fechas
     */
    public static Long calculateDuration(LocalDate start, LocalDate end) {
        return java.time.temporal.ChronoUnit.DAYS.between(start, end) + 1;
    }
}
