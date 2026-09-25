package com.ClinicaDeYmid.billing_service.domain;

import java.math.BigDecimal;
import java.util.UUID;

public record PackageCharge(UUID packageUuid, String code, String name, BigDecimal price) {

    public PackageCharge {
        DomainRules.required(packageUuid, "packageUuid");
        code = DomainRules.requiredText(code, "code", 40);
        name = DomainRules.requiredText(name, "name", 200);
        price = Money.of(DomainRules.required(price, "price"));
    }
}
