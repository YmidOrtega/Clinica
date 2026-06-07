package com.ClinicaDeYmid.billing_service.infra.feignclient.dto;

import java.math.BigDecimal;

public record PortfolioFeignDto(
        Long id,
        String name,
        String codeCups,
        String codeClinic,
        BigDecimal price
) {}
