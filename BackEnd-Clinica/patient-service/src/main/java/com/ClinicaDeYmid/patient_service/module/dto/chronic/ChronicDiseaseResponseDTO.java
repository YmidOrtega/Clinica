package com.ClinicaDeYmid.patient_service.module.dto.chronic;

import com.ClinicaDeYmid.patient_service.module.enums.DiseaseSeverity;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Schema(description = "DTO de respuesta con información completa de enfermedad crónica")
public record ChronicDiseaseResponseDTO(
        @Schema(description = "ID de la enfermedad", example = "1")
        Long id,

        @Schema(description = "ID del paciente", example = "123")
        Long patientId,

        @Schema(description = "Nombre de la enfermedad", example = "Diabetes Mellitus Tipo 2")
        String diseaseName,

        @Schema(description = "Código CIE-10", example = "E11")
        String icd10Code,

        @Schema(description = "Fecha de diagnóstico", example = "2018-03-20")
        LocalDate diagnosedDate,

        @Schema(description = "Médico que diagnosticó", example = "Dr. María López")
        String diagnosedBy,

        @Schema(description = "Severidad", example = "CONTROLLED")
        DiseaseSeverity severity,

        @Schema(description = "Nombre de severidad", example = "Controlada")
        String severityName,

        @Schema(description = "Plan de tratamiento")
        String treatmentPlan,

        @Schema(description = "Complicaciones")
        String complications,

        @Schema(description = "Fecha del último brote", example = "2024-01-10")
        LocalDate lastFlareDate,

        @Schema(description = "Frecuencia de monitoreo", example = "Cada 3 meses")
        String monitoringFrequency,

        @Schema(description = "Notas adicionales")
        String notes,

        @Schema(description = "Está activa")
        Boolean active,

        @Schema(description = "Requiere especialista")
        Boolean requiresSpecialist,

        @Schema(description = "Tipo de especialista", example = "Endocrinólogo")
        String specialistType,

        @Schema(description = "Años desde diagnóstico", example = "6")
        Long yearsSinceDiagnosis,

        @Schema(description = "Días desde último brote", example = "45")
        Long daysSinceLastFlare,

        @Schema(description = "Es crítica o no controlada")
        Boolean isCritical,

        @Schema(description = "Fecha de creación")
        LocalDateTime createdAt,

        @Schema(description = "Fecha de actualización")
        LocalDateTime updatedAt,

        @Schema(description = "Creado por usuario ID")
        Long createdBy,

        @Schema(description = "Actualizado por usuario ID")
        Long updatedBy
) {
    public ChronicDiseaseResponseDTO {
        severityName = severity != null ? severity.getDisplayName() : null;
        isCritical = severity == DiseaseSeverity.CRITICAL || severity == DiseaseSeverity.UNCONTROLLED;

        if (diagnosedDate != null) {
            yearsSinceDiagnosis = java.time.temporal.ChronoUnit.YEARS.between(
                    diagnosedDate,
                    LocalDate.now()
            );
        }

        if (lastFlareDate != null) {
            daysSinceLastFlare = java.time.temporal.ChronoUnit.DAYS.between(
                    lastFlareDate,
                    LocalDate.now()
            );
        }
    }
}