package com.ClinicaDeYmid.clients_service.module.dto;

import java.math.BigDecimal;

public record PortfolioPriceDto(
        Long id,
        String name,
        String codeCups,
        String codeClinic,
        BigDecimal price
) {}
