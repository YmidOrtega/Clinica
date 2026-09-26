package com.ClinicaDeYmid.billing_service.domain;

public enum CreditConcept {

    PARTIAL_RETURN("1", "Devolución parcial o no aceptación parcial del servicio"),
    VOID("2", "Anulación de factura electrónica"),
    DISCOUNT("3", "Rebaja o descuento parcial o total"),
    PRICE_ADJUSTMENT("4", "Ajuste de precio");

    private final String dianCode;
    private final String dianName;

    CreditConcept(String dianCode, String dianName) {
        this.dianCode = dianCode;
        this.dianName = dianName;
    }

    public String dianCode() {
        return dianCode;
    }

    public String dianName() {
        return dianName;
    }
}
