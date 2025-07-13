package com.ClinicaDeYmid.clients_service.module.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import com.ClinicaDeYmid.clients_service.module.enums.ContractStatus;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ContractDto(
        @Schema(description = "Unique identifier for the contract", example = "25")
        Long id,

        @Schema(description = "Contract name", example = "Contrato EPS Salud Total 2025")
        String contractName,

        @Schema(description = "Contract number", example = "CNTR-2025-005")
        String contractNumber,

        @Schema(description = "Agreed tariff for the contract", example = "350000.00")
        BigDecimal agreedTariff,

        @Schema(description = "Start date of the contract", example = "2025-01-01")
        LocalDate startDate,

        @Schema(description = "End date of the contract", example = "2025-12-31")
        LocalDate endDate,

        @Schema(description = "Status of the contract", example = "ACTIVE", implementation = ContractStatus.class)
        ContractStatus status,

        @Schema(description = "Whether the contract is active", example = "true")
        Boolean active
) {}
