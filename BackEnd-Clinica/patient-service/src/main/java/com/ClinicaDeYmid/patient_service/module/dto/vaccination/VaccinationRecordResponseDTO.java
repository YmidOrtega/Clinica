package com.ClinicaDeYmid.patient_service.module.dto.vaccination;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Schema(description = "DTO de respuesta con información completa de vacunación")
public record VaccinationRecordResponseDTO(
        @Schema(description = "ID del registro", example = "1")
        Long id,

        @Schema(description = "ID del paciente", example = "123")
        Long patientId,

        @Schema(description = "Nombre de la vacuna", example = "COVID-19 mRNA")
        String vaccineName,

        @Schema(description = "Tipo de vacuna", example = "mRNA")
        String vaccineType,

        @Schema(description = "Fabricante", example = "Pfizer-BioNTech")
        String manufacturer,

        @Schema(description = "Número de dosis", example = "1")
        Integer doseNumber,

        @Schema(description = "Total de dosis", example = "2")
        Integer totalDosesRequired,

        @Schema(description = "Número de lote", example = "LOT123456")
        String lotNumber,

        @Schema(description = "Fecha de administración", example = "2024-01-15")
        LocalDate administeredDate,

        @Schema(description = "Fecha de próxima dosis", example = "2024-02-15")
        LocalDate nextDoseDate,

        @Schema(description = "Administrado por", example = "Enfermera María González")
        String administeredBy,

        @Schema(description = "ID del profesional", example = "456")
        Long administeredById,

        @Schema(description = "Lugar", example = "Centro de Salud San José")
        String location,

        @Schema(description = "Sitio anatómico", example = "Brazo izquierdo")
        String siteOfAdministration,

        @Schema(description = "Vía", example = "Intramuscular")
        String route,

        @Schema(description = "Fecha de vencimiento", example = "2025-12-31")
        LocalDate expirationDate,

        @Schema(description = "Reacciones adversas")
        String adverseReactions,

        @Schema(description = "Hubo reacción")
        Boolean hadReaction,

        @Schema(description = "Severidad de reacción", example = "MILD")
        String reactionSeverity,

        @Schema(description = "Contraindicaciones")
        String contraindications,

        @Schema(description = "Notas")
        String notes,

        @Schema(description = "Verificado")
        Boolean verified,

        @Schema(description = "Verificado por", example = "Dr. Pedro Sánchez")
        String verifiedBy,

        @Schema(description = "Fecha de verificación")
        LocalDateTime verifiedDate,

        @Schema(description = "Número de certificado", example = "CERT-2024-001234")
        String certificateNumber,

        @Schema(description = "Válida para viajes")
        Boolean validForTravel,

        @Schema(description = "Es refuerzo")
        Boolean booster,

        @Schema(description = "Está vencida")
        Boolean isExpired,

        @Schema(description = "Necesita próxima dosis")
        Boolean needsNextDose,

        @Schema(description = "Esquema completo")
        Boolean isSchemeComplete,

        @Schema(description = "Días hasta próxima dosis", example = "30")
        Long daysUntilNextDose,

        @Schema(description = "Progreso del esquema", example = "50%")
        String schemeProgress,

        @Schema(description = "Fecha de creación")
        LocalDateTime createdAt,

        @Schema(description = "Fecha de actualización")
        LocalDateTime updatedAt,

        @Schema(description = "Creado por usuario ID")
        Long createdBy,

        @Schema(description = "Actualizado por usuario ID")
        Long updatedBy
) {
    public VaccinationRecordResponseDTO {
        isExpired = expirationDate != null && expirationDate.isBefore(LocalDate.now());

        if (totalDosesRequired != null && doseNumber != null) {
            isSchemeComplete = doseNumber.equals(totalDosesRequired);
            needsNextDose = nextDoseDate != null &&
                    nextDoseDate.isBefore(LocalDate.now().plusDays(30)) &&
                    doseNumber < totalDosesRequired;

            // Calcular progreso
            double progress = (doseNumber.doubleValue() / totalDosesRequired.doubleValue()) * 100;
            schemeProgress = String.format("%.0f%%", progress);
        } else {
            isSchemeComplete = false;
            needsNextDose = false;
            schemeProgress = "N/A";
        }

        if (nextDoseDate != null && nextDoseDate.isAfter(LocalDate.now())) {
            daysUntilNextDose = java.time.temporal.ChronoUnit.DAYS.between(
                    LocalDate.now(),
                    nextDoseDate
            );
        }
    }
}