package com.ClinicaDeYmid.patient_service.module.dto.medical;

import com.ClinicaDeYmid.patient_service.module.enums.BloodType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

import java.time.LocalDate;

@Schema(description = "DTO para crear o actualizar historia clínica completa")
public record MedicalHistoryRequestDTO(
        @Schema(description = "Tipo de sangre del paciente", example = "O_POSITIVE")
        BloodType bloodType,

        @Schema(description = "Factor RH", example = "POSITIVE")
        @Size(max = 10, message = "El factor RH no puede exceder 10 caracteres")
        String rhFactor,

        @Schema(description = "Presión arterial habitual", example = "120/80")
        @Pattern(regexp = "^\\d{2,3}/\\d{2,3}$", message = "Formato de presión arterial inválido (ejemplo: 120/80)")
        String bloodPressure,

        @Schema(description = "Peso en kilogramos", example = "70.5")
        @Positive(message = "El peso debe ser un número positivo")
        @DecimalMax(value = "500.0", message = "El peso no puede exceder 500 kg")
        Double weight,

        @Schema(description = "Altura en centímetros", example = "175.0")
        @Positive(message = "La altura debe ser un número positivo")
        @DecimalMax(value = "300.0", message = "La altura no puede exceder 300 cm")
        Double height,

        @Schema(description = "Estado de fumador", example = "NON_SMOKER")
        @Size(max = 50, message = "El estado de fumador no puede exceder 50 caracteres")
        String smokingStatus,

        @Schema(description = "Consumo de alcohol", example = "OCCASIONAL")
        @Size(max = 50, message = "El consumo de alcohol no puede exceder 50 caracteres")
        String alcoholConsumption,

        @Schema(description = "Frecuencia de ejercicio", example = "REGULAR")
        @Size(max = 50, message = "La frecuencia de ejercicio no puede exceder 50 caracteres")
        String exerciseFrequency,

        @Schema(description = "Tipo de dieta", example = "BALANCED")
        @Size(max = 50, message = "El tipo de dieta no puede exceder 50 caracteres")
        String dietType,

        @Schema(description = "Notas adicionales sobre la historia médica")
        @Size(max = 5000, message = "Las notas no pueden exceder 5000 caracteres")
        String notes,

        @Schema(description = "Fecha del último chequeo médico", example = "2024-01-15")
        @PastOrPresent(message = "La fecha del último chequeo no puede ser futura")
        LocalDate lastCheckupDate,

        @Schema(description = "Fecha del próximo chequeo recomendado", example = "2024-07-15")
        @Future(message = "La fecha del próximo chequeo debe ser futura")
        LocalDate nextCheckupDate,

        @Schema(description = "Indica si el paciente tiene seguro médico")
        Boolean hasInsurance,

        @Schema(description = "Proveedor del seguro médico", example = "Sanitas EPS")
        @Size(max = 200, message = "El proveedor de seguro no puede exceder 200 caracteres")
        String insuranceProvider,

        @Schema(description = "Número de póliza del seguro", example = "POL123456")
        @Size(max = 100, message = "El número de póliza no puede exceder 100 caracteres")
        String insuranceNumber
) {}