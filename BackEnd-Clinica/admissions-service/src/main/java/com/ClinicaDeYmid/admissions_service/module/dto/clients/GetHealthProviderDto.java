package com.ClinicaDeYmid.admissions_service.module.dto.clients;

public record GetHealthProviderDto(
        String nit,
        String socialReason,
        String typeProvider,
        ContractDto contract
) {
}
