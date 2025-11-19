package com.ClinicaDeYmid.suppliers_service.module.dto.unavailability;

import com.ClinicaDeYmid.suppliers_service.module.enums.UnavailabilityType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record DoctorUnavailabilityUpdateDTO(
        @Schema(description = "Tipo de ausencia", example = "SICK_LEAVE")
        UnavailabilityType type,

        @Schema(description = "Fecha de inicio", example = "2025-12-20")
        LocalDate startDate,

        @Schema(description = "Fecha de fin", example = "2025-12-31")
        LocalDate endDate,

        @Schema(description = "Razón de la ausencia", example = "Incapacidad médica")
        @Size(max = 500, message = "La razón no puede exceder 500 caracteres")
        String reason
) {
    /**
     * Valida que endDate sea posterior o igual a startDate si ambos están presentes
     */
    public DoctorUnavailabilityUpdateDTO {
        if (endDate != null && startDate != null && endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("La fecha de fin debe ser posterior o igual a la fecha de inicio");
        }
    }
}
