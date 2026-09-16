package com.ClinicaDeYmid.contracting_service.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

public enum PriceUnit {

    COP("Pesos"),
    SMLDV("Salarios mínimos legales diarios vigentes"),
    UVB("Unidades de valor básico");

    private final String label;

    PriceUnit(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public boolean fixedInPesos() {
        return this == COP;
    }

    public BigDecimal toPesos(BigDecimal value, BigDecimal unitValue) {
        return value.multiply(unitValue).setScale(2, RoundingMode.HALF_UP);
    }
}
