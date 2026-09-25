package com.ClinicaDeYmid.billing_service.domain;

import java.math.BigDecimal;

public record ComponentCharge(SurgicalComponent component, BigDecimal fullValue, BigDecimal percent,
                              BigDecimal amount) {

    public ComponentCharge {
        DomainRules.required(component, "component");
        fullValue = Money.of(DomainRules.required(fullValue, "fullValue"));
        DomainRules.required(percent, "percent");
        amount = Money.of(DomainRules.required(amount, "amount"));
    }
}
