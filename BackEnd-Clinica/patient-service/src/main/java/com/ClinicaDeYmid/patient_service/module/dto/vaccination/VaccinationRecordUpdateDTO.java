package com.ClinicaDeYmid.patient_service.module.dto.vaccination;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

import java.time.LocalDate;

@Schema(description = "DTO para actualización parcial de registro de vacunación")
public record VaccinationRecordUpdateDTO(
        @Size(max = 200)
        @Schema(description = "Nombre de la vacuna")
        String vaccineName,

        @Size(max = 100)
        @Schema(description = "Tipo de vacuna")
        String vaccineType,

        @Size(max = 200)
        @Schema(description = "Fabricante")
        String manufacturer,

        @Min(1)
        @Schema(description = "Número de dosis")
        Integer doseNumber,

        @Min(1)
        @Schema(description = "Total de dosis")
        Integer totalDosesRequired,

        @Size(max = 100)
        @Schema(description = "Número de lote")
        String lotNumber,

        @PastOrPresent
        @Schema(description = "Fecha de administración")
        LocalDate administeredDate,

        @Future
        @Schema(description = "Fecha de próxima dosis")
        LocalDate nextDoseDate,

        @Size(max = 200)
        @Schema(description = "Administrado por")
        String administeredBy,

        @Schema(description = "ID del profesional")
        Long administeredById,

        @Size(max = 200)
        @Schema(description = "Lugar")
        String location,

        @Size(max = 100)
        @Schema(description = "Sitio anatómico")
        String siteOfAdministration,

        @Size(max = 50)
        @Schema(description = "Vía")
        String route,

        @Future
        @Schema(description = "Fecha de vencimiento")
        LocalDate expirationDate,

        @Size(max = 2000)
        @Schema(description = "Reacciones adversas")
        String adverseReactions,

        @Schema(description = "Hubo reacción")
        Boolean hadReaction,

        @Size(max = 50)
        @Schema(description = "Severidad de reacción")
        String reactionSeverity,

        @Size(max = 2000)
        @Schema(description = "Contraindicaciones")
        String contraindications,

        @Size(max = 2000)
        @Schema(description = "Notas")
        String notes,

        @Schema(description = "Verificado")
        Boolean verified,

        @Size(max = 200)
        @Schema(description = "Verificado por")
        String verifiedBy,

        @Size(max = 100)
        @Schema(description = "Número de certificado")
        String certificateNumber,

        @Schema(description = "Válida para viajes")
        Boolean validForTravel,

        @Schema(description = "Es refuerzo")
        Boolean booster
) {}