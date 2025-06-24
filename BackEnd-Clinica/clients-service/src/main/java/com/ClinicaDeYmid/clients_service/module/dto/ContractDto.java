package com.ClinicaDeYmid.clients_service.module.dto;


import com.ClinicaDeYmid.clients_service.module.enums.ContractStatus;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ContractDto(
        Long id,
        String contractName,
        String contractNumber,
        BigDecimal agreedTariff,
        LocalDate startDate,
        LocalDate endDate,
        ContractStatus status,
        Boolean active
) {}
