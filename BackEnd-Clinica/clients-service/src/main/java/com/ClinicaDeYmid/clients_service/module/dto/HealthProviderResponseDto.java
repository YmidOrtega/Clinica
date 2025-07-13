package com.ClinicaDeYmid.clients_service.module.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import com.ClinicaDeYmid.clients_service.module.domain.Nit;
import com.ClinicaDeYmid.clients_service.module.enums.ContractStatus;
import com.ClinicaDeYmid.clients_service.module.enums.TypeProvider;
import java.util.List;

public record HealthProviderResponseDto(
        @Schema(description = "NIT (tax identification number)", example = "900123456-7")
        Nit nit,

        @Schema(description = "Social reason (legal name) of the provider", example = "Salud Total S.A.")
        String socialReason,

        @Schema(description = "Type of provider", example = "EPS", implementation = TypeProvider.class)
        TypeProvider typeProvider,

        @Schema(description = "List of contracts for the provider")
        List<ContractDto> contracts,

        @Schema(description = "Overall status of the main contract", example = "ACTIVE", implementation = ContractStatus.class)
        ContractStatus contractStatus
) {}
