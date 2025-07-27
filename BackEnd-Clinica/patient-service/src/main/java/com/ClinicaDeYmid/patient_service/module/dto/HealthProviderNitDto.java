package com.ClinicaDeYmid.patient_service.module.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record HealthProviderNitDto(
        @Schema(description = "NIT (tax ID number) of the health provider", example = "900123456-7")
        String nit,

        @Schema(description = "Legal name of the health provider", example = "Salud Total S.A.")
        String socialReason,

        @Schema(description = "Type of provider", example = "EPS")
        String typeProvider,

        @Schema(description = "List of contracts associated with the provider")
        List<ContractDto> contracts,

        @Schema(description = "Status of the main contract", example = "VIGENTE")
        String contractStatus
) {
}
