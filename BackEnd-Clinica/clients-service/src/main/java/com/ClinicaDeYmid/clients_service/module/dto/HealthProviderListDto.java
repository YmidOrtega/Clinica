package com.ClinicaDeYmid.clients_service.module.dto;

import com.ClinicaDeYmid.clients_service.module.domain.Nit;
import com.ClinicaDeYmid.clients_service.module.entity.Contract;
import com.ClinicaDeYmid.clients_service.module.enums.TypeProvider;

import java.util.List;

public record HealthProviderListDto(
        Long id,
        String socialReason,
        Nit nit,
        TypeProvider typeProvider,
        String address,
        String phone,
        Boolean active
) {
}
