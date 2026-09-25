package com.ClinicaDeYmid.billing_service.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class Money {

    public static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.UNNECESSARY);
    static final BigDecimal MAXIMUM = new BigDecimal("9999999999.99");

    private Money() {
    }

    public static BigDecimal of(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP);
    }

    public static BigDecimal times(BigDecimal unit, int quantity) {
        return of(unit.multiply(BigDecimal.valueOf(quantity)));
    }

    static BigDecimal positive(BigDecimal amount, String field) {
        DomainRules.required(amount, field);
        if (amount.signum() <= 0) {
            throw new BillingException.InvalidData(field, "debe ser mayor que cero");
        }
        if (amount.stripTrailingZeros().scale() > 2) {
            throw new BillingException.InvalidData(field, "no puede tener más de dos decimales");
        }
        if (amount.compareTo(MAXIMUM) > 0) {
            throw new BillingException.InvalidData(field, "supera el valor máximo permitido");
        }
        return of(amount);
    }
}
