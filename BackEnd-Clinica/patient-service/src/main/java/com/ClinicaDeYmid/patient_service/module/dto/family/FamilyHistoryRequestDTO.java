package com.ClinicaDeYmid.patient_service.module.dto.family;

import com.ClinicaDeYmid.patient_service.module.enums.FamilyRelationship;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

@Schema(description = "DTO para crear un nuevo antecedente familiar")
public record FamilyHistoryRequestDTO(
        @NotNull(message = "La relación familiar es obligatoria")
        @Schema(description = "Relación familiar", example = "FATHER", requiredMode = Schema.RequiredMode.REQUIRED)
        FamilyRelationship relationship,

        @Size(max = 200, message = "El nombre no puede exceder 200 caracteres")
        @Schema(description = "Nombre del familiar", example = "Juan Pérez")
        String relativeName,

        @NotBlank(message = "La condición es obligatoria")
        @Size(max = 200, message = "La condición no puede exceder 200 caracteres")
        @Schema(description = "Condición o enfermedad", example = "Diabetes Tipo 2", requiredMode = Schema.RequiredMode.REQUIRED)
        String condition,

        @Size(max = 10, message = "El código CIE-10 no puede exceder 10 caracteres")
        @Schema(description = "Código CIE-10", example = "E11")
        String icd10Code,

        @Min(value = 0, message = "La edad de inicio no puede ser negativa")
        @Max(value = 150, message = "La edad de inicio no puede exceder 150 años")
        @Schema(description = "Edad de inicio de la condición", example = "45")
        Integer ageOfOnset,

        @Size(max = 50, message = "El estado actual no puede exceder 50 caracteres")
        @Schema(description = "Estado actual del familiar", example = "ALIVE")
        String currentStatus,

        @Min(value = 0, message = "La edad al fallecer no puede ser negativa")
        @Max(value = 150, message = "La edad al fallecer no puede exceder 150 años")
        @Schema(description = "Edad al momento del fallecimiento", example = "70")
        Integer ageAtDeath,

        @Size(max = 200, message = "La causa de muerte no puede exceder 200 caracteres")
        @Schema(description = "Causa de fallecimiento", example = "Complicaciones cardíacas")
        String causeOfDeath,

        @Size(max = 50, message = "La severidad no puede exceder 50 caracteres")
        @Schema(description = "Severidad de la condición", example = "MODERATE")
        String severity,

        @Size(max = 2000, message = "El tratamiento no puede exceder 2000 caracteres")
        @Schema(description = "Tratamiento recibido por el familiar")
        String treatmentReceived,

        @Schema(description = "Existe riesgo genético conocido", example = "true")
        Boolean geneticRisk,

        @Schema(description = "Se recomienda screening preventivo", example = "true")
        Boolean screeningRecommended,

        @Size(max = 2000, message = "Los detalles de screening no pueden exceder 2000 caracteres")
        @Schema(description = "Detalles del screening recomendado")
        String screeningDetails,

        @Size(max = 2000, message = "Las notas no pueden exceder 2000 caracteres")
        @Schema(description = "Notas adicionales")
        String notes,

        @Schema(description = "Antecedente activo", example = "true")
        Boolean active,

        @Schema(description = "Antecedente verificado médicamente", example = "false")
        Boolean verified,

        @Size(max = 200, message = "El nombre del médico no puede exceder 200 caracteres")
        @Schema(description = "Médico que verificó el antecedente", example = "Dr. Laura Martínez")
        String verifiedBy
) {}