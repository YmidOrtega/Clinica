package com.ClinicaDeYmid.clients_service.module.dto;

import com.ClinicaDeYmid.clients_service.module.domain.Nit;
import com.ClinicaDeYmid.clients_service.module.enums.ContractStatus;
import com.ClinicaDeYmid.clients_service.module.enums.TypeProvider;

import java.util.List;

public record HealthProviderResponseDto(
        Nit nit,
        String socialReason,
        TypeProvider typeProvider,
        List<ContractDto> contracts,
        ContractStatus contractStatus

) {}
