package com.ClinicaDeYmid.admissions_service.module.dto.clients;

import java.util.List;

public record GetHealthProviderDto(
        String nit,
        String socialReason,
        String typeProvider,
        List<ContractDto> contracts,
        String contractStatus
) {
}
