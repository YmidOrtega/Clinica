package com.ClinicaDeYmid.billing_service.domain;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record PricedSale(PricingTerms terms, Map<UUID, LinePrice> lines, List<SaleLine> pending,
                         BigDecimal linesTotal, BigDecimal packagesTotal, BigDecimal total) {

    public PricedSale {
        lines = Map.copyOf(lines);
        pending = List.copyOf(pending);
    }

    public boolean complete() {
        return pending.isEmpty();
    }
}
