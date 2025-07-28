package com.ClinicaDeYmid.clients_service.module.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import com.ClinicaDeYmid.clients_service.module.enums.ContractStatus;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;

public record UpdateContractDto(
        @Schema(description = "Contract name", example = "Contrato EPS Salud Total 2025")
        @Size(max = 200, message = "El nombre del contrato no puede exceder los 200 caracteres.")
        String contractName,

        @Schema(description = "Contract number", example = "CNTR-2025-005")
        @Size(max = 100, message = "El número de contrato no puede exceder los 100 caracteres.")
        String contractNumber,

        @Schema(description = "Agreed tariff for the contract", example = "350000.00")
        @DecimalMin(value = "0.0", inclusive = false, message = "La tarifa acordada debe ser mayor a 0.")
        @Digits(integer = 13, fraction = 2, message = "La tarifa acordada debe tener máximo 13 dígitos enteros y 2 decimales.")
        BigDecimal agreedTariff,

        @Schema(description = "Start date of the contract", example = "2025-01-01")
        LocalDate startDate,

        @Schema(description = "End date of the contract", example = "2025-12-31")
        LocalDate endDate,

        @Schema(description = "Status of the contract", example = "ACTIVE", implementation = ContractStatus.class)
        ContractStatus status,

        @Schema(description = "Whether the contract is active", example = "true")
        Boolean active
) {
    @AssertTrue(message = "La fecha de finalización debe ser posterior a la fecha de inicio.")
    public boolean isEndDateValid() {
        if (startDate == null || endDate == null) {
            return true;
        }
        return endDate.isAfter(startDate) || endDate.isEqual(startDate);
    }
}
