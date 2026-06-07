package com.ClinicaDeYmid.clients_service.module.dto;

import com.ClinicaDeYmid.clients_service.module.enums.ContractStatus;

import java.math.BigDecimal;

public record ContractTariffDto(
        Long id,
        String contractNumber,
        BigDecimal agreedTariff,
        ContractStatus status
) {}
