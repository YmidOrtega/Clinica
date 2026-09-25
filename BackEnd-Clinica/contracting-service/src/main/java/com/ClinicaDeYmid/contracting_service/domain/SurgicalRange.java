package com.ClinicaDeYmid.contracting_service.domain;

import java.math.BigDecimal;

public record SurgicalRange(BigDecimal from, BigDecimal to, BigDecimal value) {

    public SurgicalRange {
        DomainRules.required(from, "from");
        DomainRules.required(to, "to");
        DomainRules.required(value, "value");
        if (from.signum() < 0 || to.compareTo(from) < 0) {
            throw new ContractingException.InvalidData("ranges", "cada rango debe ir de menor a mayor y sin negativos");
        }
        if (value.signum() < 0) {
            throw new ContractingException.InvalidData("ranges", "el valor de un rango no puede ser negativo");
        }
    }

    boolean includes(BigDecimal basis) {
        return basis.compareTo(from) >= 0 && basis.compareTo(to) <= 0;
    }
}
