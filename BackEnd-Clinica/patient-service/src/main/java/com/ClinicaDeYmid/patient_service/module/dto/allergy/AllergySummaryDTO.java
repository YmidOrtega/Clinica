package com.ClinicaDeYmid.patient_service.module.dto.allergy;

import com.ClinicaDeYmid.patient_service.module.enums.AllergySeverity;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "DTO resumido de alergia para listados")
public record AllergySummaryDTO(
        @Schema(description = "ID", example = "1")
        Long id,

        @Schema(description = "Alérgeno", example = "Penicilina")
        String allergen,

        @Schema(description = "Severidad", example = "SEVERE")
        AllergySeverity severity,

        @Schema(description = "Está activa")
        Boolean active,

        @Schema(description = "Está verificada")
        Boolean verified,

        @Schema(description = "Es crítica")
        Boolean isCritical
) {
    public AllergySummaryDTO {
        isCritical = severity == AllergySeverity.SEVERE || severity == AllergySeverity.LIFE_THREATENING;
    }
}