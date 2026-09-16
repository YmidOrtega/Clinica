package com.ClinicaDeYmid.contracting_service.domain;

public enum ContractModality {

    EVENT("Pago por evento"),
    PACKAGE("Paquete o conjunto integral"),
    CAPITATION("Capitación"),
    GLOBAL_BUDGET("Presupuesto global prospectivo");

    private final String label;

    ContractModality(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public boolean pricedPerService() {
        return this == EVENT || this == PACKAGE;
    }
}
