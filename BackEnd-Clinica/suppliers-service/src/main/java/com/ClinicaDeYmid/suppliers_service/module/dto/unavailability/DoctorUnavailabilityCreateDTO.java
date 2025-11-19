package com.ClinicaDeYmid.suppliers_service.module.dto.unavailability;

import com.ClinicaDeYmid.suppliers_service.module.enums.UnavailabilityType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record DoctorUnavailabilityCreateDTO(
        @Schema(description = "ID del doctor", example = "1", required = true)
        @NotNull(message = "El ID del doctor es obligatorio")
        Long doctorId,

        @Schema(description = "Tipo de ausencia", example = "VACATION", required = true)
        @NotNull(message = "El tipo de ausencia es obligatorio")
        UnavailabilityType type,

        @Schema(description = "Fecha de inicio", example = "2025-12-20", required = true)
        @NotNull(message = "La fecha de inicio es obligatoria")
        @FutureOrPresent(message = "La fecha de inicio debe ser presente o futura")
        LocalDate startDate,

        @Schema(description = "Fecha de fin", example = "2025-12-31", required = true)
        @NotNull(message = "La fecha de fin es obligatoria")
        @Future(message = "La fecha de fin debe ser futura")
        LocalDate endDate,

        @Schema(description = "Razón de la ausencia", example = "Vacaciones de fin de año")
        @Size(max = 500, message = "La razón no puede exceder 500 caracteres")
        String reason
) {
    /**
     * Valida que endDate sea posterior o igual a startDate
     */
    public DoctorUnavailabilityCreateDTO {
        if (endDate != null && startDate != null && endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("La fecha de fin debe ser posterior o igual a la fecha de inicio");
        }
    }
}