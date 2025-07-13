package com.ClinicaDeYmid.patient_service.module.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;

public record ContractDto(
        @Schema(description = "Unique identifier for the contract", example = "10")
        Long id,

        @Schema(description = "Name of the contract", example = "Contrato EPS Salud Total 2024")
        String contractName,

        @Schema(description = "Contract number", example = "CNTR-2024-001")
        String contractNumber,

        @Schema(description = "Agreed tariff for the contract", example = "250000.00")
        double agreedTariff,

        @Schema(description = "Contract start date", example = "2024-01-01")
        LocalDate startDate,

        @Schema(description = "Contract end date", example = "2024-12-31")
        LocalDate endDate,

        @Schema(description = "Status of the contract", example = "VIGENTE")
        String status,

        @Schema(description = "Whether the contract is active", example = "true")
        Boolean active
){}
