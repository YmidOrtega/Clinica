package com.ClinicaDeYmid.contracting_service.domain;

public enum SettlementPeriodicity {

    MONTHLY("Mensual", 1),
    BIMONTHLY("Bimestral", 2),
    QUARTERLY("Trimestral", 3);

    private final String label;
    private final int months;

    SettlementPeriodicity(String label, int months) {
        this.label = label;
        this.months = months;
    }

    public String label() {
        return label;
    }

    public int months() {
        return months;
    }
}
