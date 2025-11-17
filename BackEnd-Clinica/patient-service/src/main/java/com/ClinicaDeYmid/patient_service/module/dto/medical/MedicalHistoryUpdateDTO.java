package com.ClinicaDeYmid.patient_service.module.dto.medical;

import com.ClinicaDeYmid.patient_service.module.enums.BloodType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

import java.time.LocalDate;

@Schema(description = "DTO para actualización parcial de historia clínica")
public record MedicalHistoryUpdateDTO(
        @Schema(description = "Tipo de sangre del paciente")
        BloodType bloodType,

        @Schema(description = "Factor RH")
        @Size(max = 10)
        String rhFactor,

        @Schema(description = "Presión arterial")
        @Pattern(regexp = "^\\d{2,3}/\\d{2,3}$", message = "Formato inválido")
        String bloodPressure,

        @Schema(description = "Peso en kg")
        @Positive
        @DecimalMax("500.0")
        Double weight,

        @Schema(description = "Altura en cm")
        @Positive
        @DecimalMax("300.0")
        Double height,

        @Schema(description = "Estado de fumador")
        @Size(max = 50)
        String smokingStatus,

        @Schema(description = "Consumo de alcohol")
        @Size(max = 50)
        String alcoholConsumption,

        @Schema(description = "Frecuencia de ejercicio")
        @Size(max = 50)
        String exerciseFrequency,

        @Schema(description = "Tipo de dieta")
        @Size(max = 50)
        String dietType,

        @Schema(description = "Notas adicionales")
        @Size(max = 5000)
        String notes,

        @Schema(description = "Fecha del último chequeo")
        @PastOrPresent
        LocalDate lastCheckupDate,

        @Schema(description = "Fecha del próximo chequeo")
        @Future
        LocalDate nextCheckupDate,

        @Schema(description = "Tiene seguro médico")
        Boolean hasInsurance,

        @Schema(description = "Proveedor de seguro")
        @Size(max = 200)
        String insuranceProvider,

        @Schema(description = "Número de póliza")
        @Size(max = 100)
        String insuranceNumber
) {}