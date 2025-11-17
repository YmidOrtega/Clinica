package com.ClinicaDeYmid.patient_service.module.dto.chronic;

import com.ClinicaDeYmid.patient_service.module.enums.DiseaseSeverity;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

import java.time.LocalDate;

@Schema(description = "DTO para actualización parcial de enfermedad crónica")
public record ChronicDiseaseUpdateDTO(
        @Size(max = 200)
        @Schema(description = "Nombre de la enfermedad")
        String diseaseName,

        @Size(max = 10)
        @Schema(description = "Código CIE-10")
        String icd10Code,

        @PastOrPresent
        @Schema(description = "Fecha de diagnóstico")
        LocalDate diagnosedDate,

        @Size(max = 200)
        @Schema(description = "Médico que diagnosticó")
        String diagnosedBy,

        @Schema(description = "Severidad")
        DiseaseSeverity severity,

        @Size(max = 5000)
        @Schema(description = "Plan de tratamiento")
        String treatmentPlan,

        @Size(max = 2000)
        @Schema(description = "Complicaciones")
        String complications,

        @PastOrPresent
        @Schema(description = "Fecha del último brote")
        LocalDate lastFlareDate,

        @Size(max = 100)
        @Schema(description = "Frecuencia de monitoreo")
        String monitoringFrequency,

        @Size(max = 2000)
        @Schema(description = "Notas")
        String notes,

        @Schema(description = "Está activa")
        Boolean active,

        @Schema(description = "Requiere especialista")
        Boolean requiresSpecialist,

        @Size(max = 100)
        @Schema(description = "Tipo de especialista")
        String specialistType
) {}