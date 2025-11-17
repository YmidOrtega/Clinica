package com.ClinicaDeYmid.patient_service.module.dto.medication;

import com.ClinicaDeYmid.patient_service.module.enums.MedicationRoute;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

import java.time.LocalDate;

@Schema(description = "DTO para actualización parcial de medicamento")
public record CurrentMedicationUpdateDTO(
        @Size(max = 200)
        @Schema(description = "Nombre del medicamento")
        String medicationName,

        @Size(max = 200)
        @Schema(description = "Nombre genérico")
        String genericName,

        @Size(max = 100)
        @Schema(description = "Dosis")
        String dosage,

        @Size(max = 100)
        @Schema(description = "Frecuencia")
        String frequency,

        @Schema(description = "Vía de administración")
        MedicationRoute route,

        @Size(max = 2000)
        @Schema(description = "Instrucciones")
        String instructions,

        @PastOrPresent
        @Schema(description = "Fecha de inicio")
        LocalDate startDate,

        @Future
        @Schema(description = "Fecha de fin")
        LocalDate endDate,

        @Size(max = 200)
        @Schema(description = "Médico que prescribió")
        String prescribedBy,

        @Schema(description = "ID del médico")
        Long prescribedById,

        @Size(max = 100)
        @Schema(description = "Número de receta")
        String prescriptionNumber,

        @Size(max = 200)
        @Schema(description = "Farmacia")
        String pharmacy,

        @Min(0)
        @Schema(description = "Resurtidos restantes")
        Integer refillsRemaining,

        @Size(max = 2000)
        @Schema(description = "Razón")
        String reason,

        @Size(max = 2000)
        @Schema(description = "Efectos secundarios")
        String sideEffects,

        @Size(max = 2000)
        @Schema(description = "Interacciones")
        String interactions,

        @Size(max = 2000)
        @Schema(description = "Notas")
        String notes,

        @Schema(description = "Está activo")
        Boolean active,

        @Schema(description = "Está descontinuado")
        Boolean discontinued,

        @PastOrPresent
        @Schema(description = "Fecha de descontinuación")
        LocalDate discontinuedDate,

        @Size(max = 500)
        @Schema(description = "Razón de descontinuación")
        String discontinuedReason
) {}