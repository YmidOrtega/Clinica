package com.ClinicaDeYmid.patient_service.module.dto.medical;

import com.ClinicaDeYmid.patient_service.module.enums.BloodType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Schema(description = "DTO de respuesta con información completa de historia clínica")
public record MedicalHistoryResponseDTO(
        @Schema(description = "ID de la historia clínica", example = "1")
        Long id,

        @Schema(description = "ID del paciente", example = "123")
        Long patientId,

        @Schema(description = "Número de identificación del paciente", example = "1234567890")
        String patientIdentification,

        @Schema(description = "Nombre completo del paciente", example = "Juan Pérez")
        String patientName,

        @Schema(description = "Tipo de sangre", example = "O_POSITIVE")
        BloodType bloodType,

        @Schema(description = "Factor RH", example = "POSITIVE")
        String rhFactor,

        @Schema(description = "Presión arterial", example = "120/80")
        String bloodPressure,

        @Schema(description = "Peso en kg", example = "70.5")
        Double weight,

        @Schema(description = "Altura en cm", example = "175.0")
        Double height,

        @Schema(description = "IMC calculado", example = "23.0")
        Double bmi,

        @Schema(description = "Categoría de IMC", example = "NORMAL")
        String bmiCategory,

        @Schema(description = "Estado de fumador", example = "NON_SMOKER")
        String smokingStatus,

        @Schema(description = "Consumo de alcohol", example = "OCCASIONAL")
        String alcoholConsumption,

        @Schema(description = "Frecuencia de ejercicio", example = "REGULAR")
        String exerciseFrequency,

        @Schema(description = "Tipo de dieta", example = "BALANCED")
        String dietType,

        @Schema(description = "Notas adicionales")
        String notes,

        @Schema(description = "Fecha del último chequeo", example = "2024-01-15")
        LocalDate lastCheckupDate,

        @Schema(description = "Fecha del próximo chequeo", example = "2024-07-15")
        LocalDate nextCheckupDate,

        @Schema(description = "Días hasta el próximo chequeo", example = "180")
        Long daysUntilNextCheckup,

        @Schema(description = "Tiene seguro médico")
        Boolean hasInsurance,

        @Schema(description = "Proveedor de seguro", example = "Sanitas EPS")
        String insuranceProvider,

        @Schema(description = "Número de póliza", example = "POL123456")
        String insuranceNumber,

        @Schema(description = "Fecha de creación")
        LocalDateTime createdAt,

        @Schema(description = "Fecha de última actualización")
        LocalDateTime updatedAt,

        @Schema(description = "ID del usuario que creó el registro")
        Long createdBy,

        @Schema(description = "ID del usuario que actualizó el registro")
        Long updatedBy
) {
    /**
     * Constructor adicional para calcular campos derivados
     */
    public MedicalHistoryResponseDTO {
        // Calcular categoría de IMC
        if (bmi != null) {
            bmiCategory = calculateBmiCategory(bmi);
        }

        // Calcular días hasta próximo chequeo
        if (nextCheckupDate != null) {
            daysUntilNextCheckup = java.time.temporal.ChronoUnit.DAYS.between(
                    LocalDate.now(),
                    nextCheckupDate
            );
        }
    }

    private static String calculateBmiCategory(Double bmi) {
        if (bmi < 18.5) return "BAJO_PESO";
        if (bmi < 25.0) return "NORMAL";
        if (bmi < 30.0) return "SOBREPESO";
        if (bmi < 35.0) return "OBESIDAD_I";
        if (bmi < 40.0) return "OBESIDAD_II";
        return "OBESIDAD_III";
    }
}