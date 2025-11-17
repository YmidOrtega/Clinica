package com.ClinicaDeYmid.patient_service.module.dto.vaccination;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

import java.time.LocalDate;

@Schema(description = "DTO para crear un nuevo registro de vacunación")
public record VaccinationRecordRequestDTO(
        @NotBlank(message = "El nombre de la vacuna es obligatorio")
        @Size(max = 200, message = "El nombre no puede exceder 200 caracteres")
        @Schema(description = "Nombre de la vacuna", example = "COVID-19 mRNA", requiredMode = Schema.RequiredMode.REQUIRED)
        String vaccineName,

        @Size(max = 100, message = "El tipo no puede exceder 100 caracteres")
        @Schema(description = "Tipo de vacuna", example = "mRNA")
        String vaccineType,

        @Size(max = 200, message = "El fabricante no puede exceder 200 caracteres")
        @Schema(description = "Fabricante", example = "Pfizer-BioNTech")
        String manufacturer,

        @NotNull(message = "El número de dosis es obligatorio")
        @Min(value = 1, message = "El número de dosis debe ser al menos 1")
        @Schema(description = "Número de dosis", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
        Integer doseNumber,

        @Min(value = 1, message = "El total de dosis debe ser al menos 1")
        @Schema(description = "Total de dosis requeridas", example = "2")
        Integer totalDosesRequired,

        @Size(max = 100, message = "El lote no puede exceder 100 caracteres")
        @Schema(description = "Número de lote", example = "LOT123456")
        String lotNumber,

        @NotNull(message = "La fecha de administración es obligatoria")
        @PastOrPresent(message = "La fecha de administración no puede ser futura")
        @Schema(description = "Fecha de administración", example = "2024-01-15", requiredMode = Schema.RequiredMode.REQUIRED)
        LocalDate administeredDate,

        @Future(message = "La fecha de próxima dosis debe ser futura")
        @Schema(description = "Fecha de próxima dosis", example = "2024-02-15")
        LocalDate nextDoseDate,

        @NotBlank(message = "El administrador es obligatorio")
        @Size(max = 200, message = "El nombre no puede exceder 200 caracteres")
        @Schema(description = "Quien administró", example = "Enfermera María González", requiredMode = Schema.RequiredMode.REQUIRED)
        String administeredBy,

        @Schema(description = "ID del profesional", example = "456")
        Long administeredById,

        @NotBlank(message = "El lugar es obligatorio")
        @Size(max = 200, message = "El lugar no puede exceder 200 caracteres")
        @Schema(description = "Lugar de administración", example = "Centro de Salud San José", requiredMode = Schema.RequiredMode.REQUIRED)
        String location,

        @Size(max = 100, message = "El sitio no puede exceder 100 caracteres")
        @Schema(description = "Sitio anatómico", example = "Brazo izquierdo - músculo deltoides")
        String siteOfAdministration,

        @Size(max = 50, message = "La vía no puede exceder 50 caracteres")
        @Schema(description = "Vía de administración", example = "Intramuscular")
        String route,

        @Future(message = "La fecha de vencimiento debe ser futura")
        @Schema(description = "Fecha de vencimiento de la vacuna", example = "2025-12-31")
        LocalDate expirationDate,

        @Size(max = 2000, message = "Las reacciones no pueden exceder 2000 caracteres")
        @Schema(description = "Reacciones adversas reportadas")
        String adverseReactions,

        @Schema(description = "Hubo reacción", example = "false")
        Boolean hadReaction,

        @Size(max = 50, message = "La severidad no puede exceder 50 caracteres")
        @Schema(description = "Severidad de reacción", example = "MILD")
        String reactionSeverity,

        @Size(max = 2000, message = "Las contraindicaciones no pueden exceder 2000 caracteres")
        @Schema(description = "Contraindicaciones conocidas")
        String contraindications,

        @Size(max = 2000, message = "Las notas no pueden exceder 2000 caracteres")
        @Schema(description = "Notas adicionales")
        String notes,

        @Schema(description = "Registro verificado", example = "false")
        Boolean verified,

        @Size(max = 200, message = "El verificador no puede exceder 200 caracteres")
        @Schema(description = "Quien verificó", example = "Dr. Pedro Sánchez")
        String verifiedBy,

        @Size(max = 100, message = "El certificado no puede exceder 100 caracteres")
        @Schema(description = "Número de certificado", example = "CERT-2024-001234")
        String certificateNumber,

        @Schema(description = "Válida para viajes", example = "true")
        Boolean validForTravel,

        @Schema(description = "Es dosis de refuerzo", example = "false")
        Boolean booster
) {}