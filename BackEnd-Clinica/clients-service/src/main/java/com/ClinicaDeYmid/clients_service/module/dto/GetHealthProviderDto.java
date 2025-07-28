package com.ClinicaDeYmid.clients_service.module.dto;

import com.ClinicaDeYmid.clients_service.module.enums.TypeProvider;
import io.swagger.v3.oas.annotations.media.Schema;

public record GetHealthProviderDto(
        @Schema(description = "NIT (tax identification number)", example = "900123456-7")
        String nit,

        @Schema(description = "Social reason (legal name) of the provider", example = "Salud Total S.A.")
        String socialReason,

        @Schema(description = "Type of provider", example = "EPS", implementation = TypeProvider.class)
        String typeProvider,

        @Schema(description = "List of contracts for the provider")
        ContractDto contract
) {
}
