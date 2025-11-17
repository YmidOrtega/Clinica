package com.ClinicaDeYmid.patient_service.module.dto.medication;

import com.ClinicaDeYmid.patient_service.module.enums.MedicationRoute;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Schema(description = "DTO de respuesta con información completa de medicamento")
public record CurrentMedicationResponseDTO(
        @Schema(description = "ID del medicamento", example = "1")
        Long id,

        @Schema(description = "ID del paciente", example = "123")
        Long patientId,

        @Schema(description = "Nombre del medicamento", example = "Metformina")
        String medicationName,

        @Schema(description = "Nombre genérico", example = "Metformin Hydrochloride")
        String genericName,

        @Schema(description = "Dosis", example = "500mg")
        String dosage,

        @Schema(description = "Frecuencia", example = "Cada 12 horas")
        String frequency,

        @Schema(description = "Vía de administración", example = "ORAL")
        MedicationRoute route,

        @Schema(description = "Nombre de vía", example = "Oral")
        String routeName,

        @Schema(description = "Instrucciones", example = "Tomar con alimentos")
        String instructions,

        @Schema(description = "Fecha de inicio", example = "2024-01-15")
        LocalDate startDate,

        @Schema(description = "Fecha de fin", example = "2024-07-15")
        LocalDate endDate,

        @Schema(description = "Médico que prescribió", example = "Dr. Carlos Ramírez")
        String prescribedBy,

        @Schema(description = "ID del médico", example = "123")
        Long prescribedById,

        @Schema(description = "Número de receta", example = "RX-2024-001234")
        String prescriptionNumber,

        @Schema(description = "Farmacia", example = "Farmacia San Rafael")
        String pharmacy,

        @Schema(description = "Resurtidos restantes", example = "3")
        Integer refillsRemaining,

        @Schema(description = "Razón", example = "Control de diabetes tipo 2")
        String reason,

        @Schema(description = "Efectos secundarios")
        String sideEffects,

        @Schema(description = "Interacciones")
        String interactions,

        @Schema(description = "Notas")
        String notes,

        @Schema(description = "Está activo")
        Boolean active,

        @Schema(description = "Está descontinuado")
        Boolean discontinued,

        @Schema(description = "Fecha de descontinuación")
        LocalDate discontinuedDate,

        @Schema(description = "Razón de descontinuación")
        String discontinuedReason,

        @Schema(description = "Está vencido")
        Boolean isExpired,

        @Schema(description = "Necesita resurtido")
        Boolean needsRefill,

        @Schema(description = "Días de tratamiento", example = "180")
        Long daysOfTreatment,

        @Schema(description = "Días hasta vencimiento", example = "90")
        Long daysUntilExpiration,

        @Schema(description = "Fecha de creación")
        LocalDateTime createdAt,

        @Schema(description = "Fecha de actualización")
        LocalDateTime updatedAt,

        @Schema(description = "Creado por usuario ID")
        Long createdBy,

        @Schema(description = "Actualizado por usuario ID")
        Long updatedBy
) {
    public CurrentMedicationResponseDTO {
        routeName = route != null ? route.getDisplayName() : null;
        isExpired = endDate != null && endDate.isBefore(LocalDate.now());
        needsRefill = active && !discontinued && (refillsRemaining == null || refillsRemaining <= 1);

        if (startDate != null) {
            daysOfTreatment = java.time.temporal.ChronoUnit.DAYS.between(
                    startDate,
                    LocalDate.now()
            );
        }

        if (endDate != null && endDate.isAfter(LocalDate.now())) {
            daysUntilExpiration = java.time.temporal.ChronoUnit.DAYS.between(
                    LocalDate.now(),
                    endDate
            );
        }
    }
}