package com.ClinicaDeYmid.patient_service.module.dto.allergy;

import com.ClinicaDeYmid.patient_service.module.enums.AllergyReactionType;
import com.ClinicaDeYmid.patient_service.module.enums.AllergySeverity;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

import java.time.LocalDate;

@Schema(description = "DTO para crear una nueva alergia")
public record AllergyRequestDTO(
        @NotBlank(message = "El alérgeno es obligatorio")
        @Size(max = 200, message = "El alérgeno no puede exceder 200 caracteres")
        @Schema(description = "Alérgeno que causa la reacción", example = "Penicilina", requiredMode = Schema.RequiredMode.REQUIRED)
        String allergen,

        @NotNull(message = "La severidad es obligatoria")
        @Schema(description = "Severidad de la alergia", example = "SEVERE", requiredMode = Schema.RequiredMode.REQUIRED)
        AllergySeverity severity,

        @Schema(description = "Tipo de reacción alérgica", example = "RESPIRATORY")
        AllergyReactionType reactionType,

        @Size(max = 2000, message = "Los síntomas no pueden exceder 2000 caracteres")
        @Schema(description = "Síntomas específicos", example = "Dificultad para respirar, sarpullido")
        String symptoms,

        @PastOrPresent(message = "La fecha de diagnóstico no puede ser futura")
        @Schema(description = "Fecha de diagnóstico", example = "2020-05-15")
        LocalDate diagnosedDate,

        @Size(max = 200, message = "El nombre del médico no puede exceder 200 caracteres")
        @Schema(description = "Médico que diagnosticó", example = "Dr. Juan Pérez")
        String diagnosedBy,

        @Size(max = 2000, message = "El tratamiento no puede exceder 2000 caracteres")
        @Schema(description = "Tratamiento recomendado")
        String treatment,

        @Size(max = 2000, message = "Las notas no pueden exceder 2000 caracteres")
        @Schema(description = "Notas adicionales")
        String notes,

        @Schema(description = "Alergia está activa", example = "true")
        Boolean active,

        @Schema(description = "Alergia verificada médicamente", example = "false")
        Boolean verified
) {}