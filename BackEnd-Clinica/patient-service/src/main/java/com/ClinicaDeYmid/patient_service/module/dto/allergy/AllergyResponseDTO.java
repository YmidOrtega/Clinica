package com.ClinicaDeYmid.patient_service.module.dto.allergy;

import com.ClinicaDeYmid.patient_service.module.enums.AllergyReactionType;
import com.ClinicaDeYmid.patient_service.module.enums.AllergySeverity;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Schema(description = "DTO de respuesta con información completa de alergia")
public record AllergyResponseDTO(
        @Schema(description = "ID de la alergia", example = "1")
        Long id,

        @Schema(description = "ID del paciente", example = "123")
        Long patientId,

        @Schema(description = "Alérgeno", example = "Penicilina")
        String allergen,

        @Schema(description = "Severidad", example = "SEVERE")
        AllergySeverity severity,

        @Schema(description = "Nombre de severidad", example = "Severa")
        String severityName,

        @Schema(description = "Tipo de reacción", example = "RESPIRATORY")
        AllergyReactionType reactionType,

        @Schema(description = "Nombre de tipo de reacción", example = "Respiratoria")
        String reactionTypeName,

        @Schema(description = "Síntomas")
        String symptoms,

        @Schema(description = "Fecha de diagnóstico", example = "2020-05-15")
        LocalDate diagnosedDate,

        @Schema(description = "Médico que diagnosticó", example = "Dr. Juan Pérez")
        String diagnosedBy,

        @Schema(description = "Tratamiento")
        String treatment,

        @Schema(description = "Notas adicionales")
        String notes,

        @Schema(description = "Está activa")
        Boolean active,

        @Schema(description = "Está verificada")
        Boolean verified,

        @Schema(description = "Es alergia crítica")
        Boolean isCritical,

        @Schema(description = "Años desde el diagnóstico", example = "4")
        Long yearsSinceDiagnosis,

        @Schema(description = "Fecha de creación")
        LocalDateTime createdAt,

        @Schema(description = "Fecha de actualización")
        LocalDateTime updatedAt,

        @Schema(description = "Creado por usuario ID")
        Long createdBy,

        @Schema(description = "Actualizado por usuario ID")
        Long updatedBy
) {
    public AllergyResponseDTO {
        // Calcular campos derivados
        severityName = severity != null ? severity.getDisplayName() : null;
        reactionTypeName = reactionType != null ? reactionType.getDisplayName() : null;
        isCritical = severity == AllergySeverity.SEVERE || severity == AllergySeverity.LIFE_THREATENING;

        if (diagnosedDate != null) {
            yearsSinceDiagnosis = java.time.temporal.ChronoUnit.YEARS.between(
                    diagnosedDate,
                    LocalDate.now()
            );
        }
    }
}