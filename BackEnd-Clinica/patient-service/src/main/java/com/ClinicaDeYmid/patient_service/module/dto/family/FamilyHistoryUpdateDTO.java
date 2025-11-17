package com.ClinicaDeYmid.patient_service.module.dto.family;

import com.ClinicaDeYmid.patient_service.module.enums.FamilyRelationship;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

@Schema(description = "DTO para actualización parcial de antecedente familiar")
public record FamilyHistoryUpdateDTO(
        @Schema(description = "Relación familiar")
        FamilyRelationship relationship,

        @Size(max = 200)
        @Schema(description = "Nombre del familiar")
        String relativeName,

        @Size(max = 200)
        @Schema(description = "Condición")
        String condition,

        @Size(max = 10)
        @Schema(description = "Código CIE-10")
        String icd10Code,

        @Min(0)
        @Max(150)
        @Schema(description = "Edad de inicio")
        Integer ageOfOnset,

        @Size(max = 50)
        @Schema(description = "Estado actual")
        String currentStatus,

        @Min(0)
        @Max(150)
        @Schema(description = "Edad al fallecer")
        Integer ageAtDeath,

        @Size(max = 200)
        @Schema(description = "Causa de muerte")
        String causeOfDeath,

        @Size(max = 50)
        @Schema(description = "Severidad")
        String severity,

        @Size(max = 2000)
        @Schema(description = "Tratamiento recibido")
        String treatmentReceived,

        @Schema(description = "Riesgo genético")
        Boolean geneticRisk,

        @Schema(description = "Screening recomendado")
        Boolean screeningRecommended,

        @Size(max = 2000)
        @Schema(description = "Detalles de screening")
        String screeningDetails,

        @Size(max = 2000)
        @Schema(description = "Notas")
        String notes,

        @Schema(description = "Está activo")
        Boolean active,

        @Schema(description = "Está verificado")
        Boolean verified,

        @Size(max = 200)
        @Schema(description = "Verificado por")
        String verifiedBy
) {}