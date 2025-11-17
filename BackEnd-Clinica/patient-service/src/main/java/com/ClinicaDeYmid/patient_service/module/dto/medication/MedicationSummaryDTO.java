package com.ClinicaDeYmid.patient_service.module.dto.medication;

import com.ClinicaDeYmid.patient_service.module.enums.MedicationRoute;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

@Schema(description = "DTO resumido de medicamento para listados")
public record MedicationSummaryDTO(
        @Schema(description = "ID", example = "1")
        Long id,

        @Schema(description = "Nombre del medicamento", example = "Metformina")
        String medicationName,

        @Schema(description = "Dosis", example = "500mg")
        String dosage,

        @Schema(description = "Frecuencia", example = "Cada 12 horas")
        String frequency,

        @Schema(description = "Vía", example = "ORAL")
        MedicationRoute route,

        @Schema(description = "Fecha de inicio", example = "2024-01-15")
        LocalDate startDate,

        @Schema(description = "Está activo")
        Boolean active,

        @Schema(description = "Está descontinuado")
        Boolean discontinued,

        @Schema(description = "Necesita resurtido")
        Boolean needsRefill
) {
    public MedicationSummaryDTO {
        needsRefill = active && !discontinued;
    }
}