package com.ClinicaDeYmid.patient_service.module.dto.medication;

import com.ClinicaDeYmid.patient_service.module.enums.MedicationRoute;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

import java.time.LocalDate;

@Schema(description = "DTO para crear un nuevo medicamento actual")
public record CurrentMedicationRequestDTO(
        @NotBlank(message = "El nombre del medicamento es obligatorio")
        @Size(max = 200, message = "El nombre no puede exceder 200 caracteres")
        @Schema(description = "Nombre del medicamento", example = "Metformina", requiredMode = Schema.RequiredMode.REQUIRED)
        String medicationName,

        @Size(max = 200, message = "El nombre genérico no puede exceder 200 caracteres")
        @Schema(description = "Nombre genérico", example = "Metformin Hydrochloride")
        String genericName,

        @NotBlank(message = "La dosis es obligatoria")
        @Size(max = 100, message = "La dosis no puede exceder 100 caracteres")
        @Schema(description = "Dosis", example = "500mg", requiredMode = Schema.RequiredMode.REQUIRED)
        String dosage,

        @NotBlank(message = "La frecuencia es obligatoria")
        @Size(max = 100, message = "La frecuencia no puede exceder 100 caracteres")
        @Schema(description = "Frecuencia", example = "Cada 12 horas", requiredMode = Schema.RequiredMode.REQUIRED)
        String frequency,

        @Schema(description = "Vía de administración", example = "ORAL")
        MedicationRoute route,

        @Size(max = 2000, message = "Las instrucciones no pueden exceder 2000 caracteres")
        @Schema(description = "Instrucciones de uso", example = "Tomar con alimentos")
        String instructions,

        @NotNull(message = "La fecha de inicio es obligatoria")
        @PastOrPresent(message = "La fecha de inicio no puede ser futura")
        @Schema(description = "Fecha de inicio", example = "2024-01-15", requiredMode = Schema.RequiredMode.REQUIRED)
        LocalDate startDate,

        @Future(message = "La fecha de fin debe ser futura")
        @Schema(description = "Fecha de fin", example = "2024-07-15")
        LocalDate endDate,

        @NotBlank(message = "El médico que prescribió es obligatorio")
        @Size(max = 200, message = "El nombre del médico no puede exceder 200 caracteres")
        @Schema(description = "Médico que prescribió", example = "Dr. Carlos Ramírez", requiredMode = Schema.RequiredMode.REQUIRED)
        String prescribedBy,

        @Schema(description = "ID del médico", example = "123")
        Long prescribedById,

        @Size(max = 100, message = "El número de receta no puede exceder 100 caracteres")
        @Schema(description = "Número de receta", example = "RX-2024-001234")
        String prescriptionNumber,

        @Size(max = 200, message = "El nombre de la farmacia no puede exceder 200 caracteres")
        @Schema(description = "Farmacia", example = "Farmacia San Rafael")
        String pharmacy,

        @Min(value = 0, message = "Los resurtidos no pueden ser negativos")
        @Schema(description = "Resurtidos restantes", example = "3")
        Integer refillsRemaining,

        @Size(max = 2000, message = "La razón no puede exceder 2000 caracteres")
        @Schema(description = "Razón de prescripción", example = "Control de diabetes tipo 2")
        String reason,

        @Size(max = 2000, message = "Los efectos secundarios no pueden exceder 2000 caracteres")
        @Schema(description = "Efectos secundarios")
        String sideEffects,

        @Size(max = 2000, message = "Las interacciones no pueden exceder 2000 caracteres")
        @Schema(description = "Interacciones medicamentosas")
        String interactions,

        @Size(max = 2000, message = "Las notas no pueden exceder 2000 caracteres")
        @Schema(description = "Notas adicionales")
        String notes,

        @Schema(description = "Medicamento activo", example = "true")
        Boolean active,

        @Schema(description = "Medicamento descontinuado", example = "false")
        Boolean discontinued,

        @PastOrPresent(message = "La fecha de descontinuación no puede ser futura")
        @Schema(description = "Fecha de descontinuación")
        LocalDate discontinuedDate,

        @Size(max = 500, message = "La razón de descontinuación no puede exceder 500 caracteres")
        @Schema(description = "Razón de descontinuación")
        String discontinuedReason
) {}