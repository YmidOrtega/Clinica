package com.ClinicaDeYmid.patient_service.module.dto.family;

import com.ClinicaDeYmid.patient_service.module.enums.FamilyRelationship;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "DTO de respuesta con información completa de antecedente familiar")
public record FamilyHistoryResponseDTO(
        @Schema(description = "ID del antecedente", example = "1")
        Long id,

        @Schema(description = "ID del paciente", example = "123")
        Long patientId,

        @Schema(description = "Relación familiar", example = "FATHER")
        FamilyRelationship relationship,

        @Schema(description = "Nombre de relación", example = "Padre")
        String relationshipName,

        @Schema(description = "Nombre del familiar", example = "Juan Pérez")
        String relativeName,

        @Schema(description = "Condición", example = "Diabetes Tipo 2")
        String condition,

        @Schema(description = "Código CIE-10", example = "E11")
        String icd10Code,

        @Schema(description = "Edad de inicio", example = "45")
        Integer ageOfOnset,

        @Schema(description = "Estado actual", example = "ALIVE")
        String currentStatus,

        @Schema(description = "Edad al fallecer", example = "70")
        Integer ageAtDeath,

        @Schema(description = "Causa de muerte", example = "Complicaciones cardíacas")
        String causeOfDeath,

        @Schema(description = "Severidad", example = "MODERATE")
        String severity,

        @Schema(description = "Tratamiento recibido")
        String treatmentReceived,

        @Schema(description = "Riesgo genético")
        Boolean geneticRisk,

        @Schema(description = "Screening recomendado")
        Boolean screeningRecommended,

        @Schema(description = "Detalles de screening")
        String screeningDetails,

        @Schema(description = "Notas")
        String notes,

        @Schema(description = "Está activo")
        Boolean active,

        @Schema(description = "Está verificado")
        Boolean verified,

        @Schema(description = "Verificado por", example = "Dr. Laura Martínez")
        String verifiedBy,

        @Schema(description = "Fecha de verificación")
        LocalDateTime verifiedDate,

        @Schema(description = "Requiere atención médica")
        Boolean requiresMedicalAttention,

        @Schema(description = "Nivel de riesgo para el paciente", example = "MODERATE")
        String riskLevel,

        @Schema(description = "Fecha de creación")
        LocalDateTime createdAt,

        @Schema(description = "Fecha de actualización")
        LocalDateTime updatedAt,

        @Schema(description = "Creado por usuario ID")
        Long createdBy,

        @Schema(description = "Actualizado por usuario ID")
        Long updatedBy
) {
    public FamilyHistoryResponseDTO {
        relationshipName = relationship != null ? relationship.getDisplayName() : null;
        requiresMedicalAttention = (geneticRisk != null && geneticRisk) ||
                (screeningRecommended != null && screeningRecommended);

        // Calcular nivel de riesgo basado en varios factores
        if (geneticRisk != null && geneticRisk) {
            if (ageOfOnset != null && ageOfOnset < 50) {
                riskLevel = "HIGH";
            } else if (screeningRecommended != null && screeningRecommended) {
                riskLevel = "MODERATE";
            } else {
                riskLevel = "LOW";
            }
        } else {
            riskLevel = "LOW";
        }
    }
}