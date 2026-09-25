package com.ClinicaDeYmid.billing_service.domain;

import java.math.BigDecimal;
import java.util.List;

public record SurgicalDetail(int order, boolean principal, boolean sameRoute, BigDecimal basis,
                             List<ComponentCharge> components) {

    public SurgicalDetail {
        components = List.copyOf(components);
    }

    public BigDecimal total() {
        return components.stream().map(ComponentCharge::amount).reduce(Money.ZERO, BigDecimal::add);
    }
}
