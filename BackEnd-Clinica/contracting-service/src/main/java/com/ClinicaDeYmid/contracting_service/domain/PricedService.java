package com.ClinicaDeYmid.contracting_service.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

public record PricedService(String cupsCode, int quantity, BigDecimal unitPrice, BigDecimal lineTotal,
                            PriceOrigin origin, String description, UUID referenceUuid, String referenceCode) {

    public static PricedService priced(String cupsCode, int quantity, BigDecimal unitPrice, PriceOrigin origin,
                                       String description, UUID referenceUuid, String referenceCode) {
        BigDecimal price = unitPrice.setScale(2, RoundingMode.HALF_UP);
        return new PricedService(cupsCode, quantity, price,
                price.multiply(BigDecimal.valueOf(quantity)).setScale(2, RoundingMode.HALF_UP),
                origin, description, referenceUuid, referenceCode);
    }

    public static PricedService covered(String cupsCode, int quantity, PriceOrigin origin, String description,
                                        UUID referenceUuid, String referenceCode) {
        return new PricedService(cupsCode, quantity, BigDecimal.ZERO.setScale(2, RoundingMode.UNNECESSARY),
                BigDecimal.ZERO.setScale(2, RoundingMode.UNNECESSARY), origin, description, referenceUuid, referenceCode);
    }
}
