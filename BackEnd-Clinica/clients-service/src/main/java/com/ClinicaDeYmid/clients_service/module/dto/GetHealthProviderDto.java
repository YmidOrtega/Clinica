package com.ClinicaDeYmid.clients_service.module.dto;

import com.ClinicaDeYmid.clients_service.module.domain.Nit;
import com.ClinicaDeYmid.clients_service.module.entity.Contract;
import com.ClinicaDeYmid.clients_service.module.enums.ContractStatus;
import com.ClinicaDeYmid.clients_service.module.enums.TypeProvider;

import java.util.List;

public record GetHealthProviderDto(
        Nit nit,
        String socialReason,
        TypeProvider typeProvider,
        String createdAt,
        List<Contract> contracts,
        ContractStatus contractStatus

) {
}
