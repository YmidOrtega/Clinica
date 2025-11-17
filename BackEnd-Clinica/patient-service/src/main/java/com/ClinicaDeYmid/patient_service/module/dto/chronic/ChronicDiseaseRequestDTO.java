package com.ClinicaDeYmid.patient_service.module.dto.chronic;

import com.ClinicaDeYmid.patient_service.module.enums.DiseaseSeverity;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

import java.time.LocalDate;

@Schema(description = "DTO para crear una nueva enfermedad crónica")
public record ChronicDiseaseRequestDTO(
        @NotBlank(message = "El nombre de la enfermedad es obligatorio")
        @Size(max = 200, message = "El nombre no puede exceder 200 caracteres")
        @Schema(description = "Nombre de la enfermedad", example = "Diabetes Mellitus Tipo 2", requiredMode = Schema.RequiredMode.REQUIRED)
        String diseaseName,

        @Size(max = 10, message = "El código CIE-10 no puede exceder 10 caracteres")
        @Schema(description = "Código CIE-10", example = "E11")
        String icd10Code,

        @PastOrPresent(message = "La fecha de diagnóstico no puede ser futura")
        @Schema(description = "Fecha de diagnóstico", example = "2018-03-20")
        LocalDate diagnosedDate,

        @Size(max = 200, message = "El nombre del médico no puede exceder 200 caracteres")
        @Schema(description = "Médico que diagnosticó", example = "Dr. María López")
        String diagnosedBy,

        @NotNull(message = "La severidad es obligatoria")
        @Schema(description = "Severidad de la enfermedad", example = "CONTROLLED", requiredMode = Schema.RequiredMode.REQUIRED)
        DiseaseSeverity severity,

        @Size(max = 5000, message = "El plan de tratamiento no puede exceder 5000 caracteres")
        @Schema(description = "Plan de tratamiento actual")
        String treatmentPlan,

        @Size(max = 2000, message = "Las complicaciones no pueden exceder 2000 caracteres")
        @Schema(description = "Complicaciones conocidas")
        String complications,

        @PastOrPresent(message = "La fecha del último brote no puede ser futura")
        @Schema(description = "Fecha del último brote", example = "2024-01-10")
        LocalDate lastFlareDate,

        @Size(max = 100, message = "La frecuencia de monitoreo no puede exceder 100 caracteres")
        @Schema(description = "Frecuencia de monitoreo", example = "Cada 3 meses")
        String monitoringFrequency,

        @Size(max = 2000, message = "Las notas no pueden exceder 2000 caracteres")
        @Schema(description = "Notas adicionales")
        String notes,

        @Schema(description = "Enfermedad activa", example = "true")
        Boolean active,

        @Schema(description = "Requiere especialista", example = "true")
        Boolean requiresSpecialist,

        @Size(max = 100, message = "El tipo de especialista no puede exceder 100 caracteres")
        @Schema(description = "Tipo de especialista", example = "Endocrinólogo")
        String specialistType
) {}