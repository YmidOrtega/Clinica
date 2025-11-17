package com.ClinicaDeYmid.patient_service.module.dto.allergy;

import com.ClinicaDeYmid.patient_service.module.enums.AllergyReactionType;
import com.ClinicaDeYmid.patient_service.module.enums.AllergySeverity;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

import java.time.LocalDate;

@Schema(description = "DTO para actualización parcial de alergia")
public record AllergyUpdateDTO(
        @Size(max = 200)
        @Schema(description = "Alérgeno")
        String allergen,

        @Schema(description = "Severidad")
        AllergySeverity severity,

        @Schema(description = "Tipo de reacción")
        AllergyReactionType reactionType,

        @Size(max = 2000)
        @Schema(description = "Síntomas")
        String symptoms,

        @PastOrPresent
        @Schema(description = "Fecha de diagnóstico")
        LocalDate diagnosedDate,

        @Size(max = 200)
        @Schema(description = "Médico que diagnosticó")
        String diagnosedBy,

        @Size(max = 2000)
        @Schema(description = "Tratamiento")
        String treatment,

        @Size(max = 2000)
        @Schema(description = "Notas")
        String notes,

        @Schema(description = "Está activa")
        Boolean active,

        @Schema(description = "Está verificada")
        Boolean verified
) {}