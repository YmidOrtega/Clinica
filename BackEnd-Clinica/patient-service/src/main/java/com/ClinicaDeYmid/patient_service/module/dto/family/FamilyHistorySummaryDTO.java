package com.ClinicaDeYmid.patient_service.module.dto.family;

import com.ClinicaDeYmid.patient_service.module.enums.FamilyRelationship;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "DTO resumido de antecedente familiar para listados")
public record FamilyHistorySummaryDTO(
        @Schema(description = "ID", example = "1")
        Long id,

        @Schema(description = "Relación", example = "FATHER")
        FamilyRelationship relationship,

        @Schema(description = "Nombre de relación", example = "Padre")
        String relationshipName,

        @Schema(description = "Condición", example = "Diabetes Tipo 2")
        String condition,

        @Schema(description = "Riesgo genético")
        Boolean geneticRisk,

        @Schema(description = "Screening recomendado")
        Boolean screeningRecommended,

        @Schema(description = "Está activo")
        Boolean active,

        @Schema(description = "Está verificado")
        Boolean verified
) {
    public FamilyHistorySummaryDTO {
        relationshipName = relationship != null ? relationship.getDisplayName() : null;
    }
}