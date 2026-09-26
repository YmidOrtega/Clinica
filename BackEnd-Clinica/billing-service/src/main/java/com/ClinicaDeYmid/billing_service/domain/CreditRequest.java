package com.ClinicaDeYmid.billing_service.domain;

import java.math.BigDecimal;

public record CreditRequest(int invoiceLinePosition, Integer quantity, BigDecimal amount) {

    public CreditRequest {
        if ((quantity == null) == (amount == null)) {
            throw new BillingException.InvalidData("lines",
                    "debe indicar por cada línea la cantidad o el valor a acreditar, no ambos");
        }
        if (quantity != null && quantity < 1) {
            throw new BillingException.InvalidData("lines.quantity", "debe ser al menos 1");
        }
        if (amount != null && amount.signum() <= 0) {
            throw new BillingException.InvalidData("lines.amount", "debe ser mayor que cero");
        }
    }
}
