package com.ClinicaDeYmid.suppliers_service.module.dto;

import com.ClinicaDeYmid.suppliers_service.module.domain.Nit;
import com.ClinicaDeYmid.suppliers_service.module.enums.TypeProvider;

public record HealthProviderResponseDto (
        Nit nit,
        String socialReason,
        TypeProvider typeProvider,
        String contractStatus,
        String phone,
        String address,
        String createdAt

) {
}
