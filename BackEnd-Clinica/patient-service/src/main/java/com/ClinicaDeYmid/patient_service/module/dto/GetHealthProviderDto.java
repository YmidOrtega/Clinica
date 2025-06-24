package com.ClinicaDeYmid.patient_service.module.dto;

import java.util.List;

public record GetHealthProviderDto(
        String nit,
        String socialReason,
        String typeProvider,
        List<ContractDto> contracts,
        String contractStatus
) {
}
