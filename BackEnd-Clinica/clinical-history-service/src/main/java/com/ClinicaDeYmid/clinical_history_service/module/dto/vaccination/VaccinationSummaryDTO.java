package com.ClinicaDeYmid.clinical_history_service.module.dto.vaccination;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

@Schema(description = "DTO resumido de vacunación para listados")
public record VaccinationSummaryDTO(
        @Schema(description = "ID", example = "1")
        Long id,

        @Schema(description = "Nombre de la vacuna", example = "COVID-19 mRNA")
        String vaccineName,

        @Schema(description = "Fabricante", example = "Pfizer-BioNTech")
        String manufacturer,

        @Schema(description = "Dosis", example = "1")
        Integer doseNumber,

        @Schema(description = "Total dosis", example = "2")
        Integer totalDosesRequired,

        @Schema(description = "Fecha de administración", example = "2024-01-15")
        LocalDate administeredDate,

        @Schema(description = "Próxima dosis", example = "2024-02-15")
        LocalDate nextDoseDate,

        @Schema(description = "Esquema completo")
        Boolean isSchemeComplete,

        @Schema(description = "Válida para viajes")
        Boolean validForTravel,

        @Schema(description = "Verificado")
        Boolean verified
) {
    public VaccinationSummaryDTO {
        isSchemeComplete = totalDosesRequired != null &&
                doseNumber != null &&
                doseNumber.equals(totalDosesRequired);
    }
}