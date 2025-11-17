package com.ClinicaDeYmid.patient_service.module.dto.chronic;

import com.ClinicaDeYmid.patient_service.module.enums.DiseaseSeverity;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "DTO resumido de enfermedad crónica para listados")
public record ChronicDiseaseSummaryDTO(
        @Schema(description = "ID", example = "1")
        Long id,

        @Schema(description = "Nombre de la enfermedad", example = "Diabetes Mellitus Tipo 2")
        String diseaseName,

        @Schema(description = "Severidad", example = "CONTROLLED")
        DiseaseSeverity severity,

        @Schema(description = "Está activa")
        Boolean active,

        @Schema(description = "Requiere especialista")
        Boolean requiresSpecialist,

        @Schema(description = "Es crítica")
        Boolean isCritical
) {
    public ChronicDiseaseSummaryDTO {
        isCritical = severity == DiseaseSeverity.CRITICAL || severity == DiseaseSeverity.UNCONTROLLED;
    }
}