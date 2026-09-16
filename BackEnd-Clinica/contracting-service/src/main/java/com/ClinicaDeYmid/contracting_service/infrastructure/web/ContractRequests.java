package com.ClinicaDeYmid.contracting_service.infrastructure.web;

import com.ClinicaDeYmid.contracting_service.domain.ContractModality;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

final class ContractRequests {

    private static final String REQUIRED = "es obligatorio";

    private ContractRequests() {
    }

    record Draft(@NotNull(message = REQUIRED) UUID payerUuid, String number, String name,
                 @NotNull(message = REQUIRED) ContractModality modality,
                 @NotNull(message = REQUIRED) LocalDate validFrom, LocalDate validTo) {
    }

    record TariffTerms(UUID tariffVersionUuid, BigDecimal factor) {
    }

    record Rename(String name) {
    }

    record Validity(LocalDate validTo) {
    }

    record StatusChange(String reason) {
    }

    record TariffExceptionRequest(String cupsCode, BigDecimal agreedPrice, String reason, LocalDate validFrom) {
    }

    record PackageRequest(String code, String name, BigDecimal price, Set<String> includedCodes, LocalDate validFrom) {
    }

    record Revocation(LocalDate from, String reason) {
    }
}
