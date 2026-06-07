package com.ClinicaDeYmid.billing_service.infra.feignclient.dto;

import java.math.BigDecimal;

public record ContractFeignDto(
        Long id,
        String contractNumber,
        BigDecimal agreedTariff,
        String status
) {}
