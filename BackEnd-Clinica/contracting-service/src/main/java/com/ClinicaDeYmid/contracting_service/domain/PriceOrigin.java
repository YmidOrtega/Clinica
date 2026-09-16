package com.ClinicaDeYmid.contracting_service.domain;

public enum PriceOrigin {

    PACKAGE("Incluido en un paquete pactado"),
    CONTRACT_EXCEPTION("Precio pactado por fuera del manual"),
    TARIFF_MANUAL("Manual tarifario con el factor del contrato"),
    CAPITATION("Cubierto por la capitación"),
    GLOBAL_BUDGET("Cubierto por el presupuesto global"),
    UNPRICED("Sin tarifa para ese código");

    private final String label;

    PriceOrigin(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public boolean billablePerService() {
        return this == CONTRACT_EXCEPTION || this == TARIFF_MANUAL;
    }
}
