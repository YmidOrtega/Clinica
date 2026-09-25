package com.ClinicaDeYmid.billing_service.domain;

import java.math.BigDecimal;
import java.util.UUID;

public record LinePrice(PriceOrigin origin, BigDecimal unitPrice, BigDecimal lineTotal, UUID referenceUuid,
                        String referenceCode) {

    public LinePrice {
        DomainRules.required(origin, "origin");
        unitPrice = Money.of(DomainRules.required(unitPrice, "unitPrice"));
        lineTotal = Money.of(DomainRules.required(lineTotal, "lineTotal"));
        if (unitPrice.signum() < 0 || lineTotal.signum() < 0) {
            throw new BillingException.InvalidData("unitPrice", "no puede ser negativo");
        }
        referenceCode = DomainRules.optionalText(referenceCode, "referenceCode", 40);
    }

    public static LinePrice unpriced() {
        return new LinePrice(PriceOrigin.UNPRICED, Money.ZERO, Money.ZERO, null, null);
    }

    public static LinePrice manual(BigDecimal unitPrice, int quantity) {
        return new LinePrice(PriceOrigin.MANUAL, unitPrice, Money.times(unitPrice, quantity), null, null);
    }

    public boolean pending() {
        return origin == PriceOrigin.UNPRICED;
    }
}
