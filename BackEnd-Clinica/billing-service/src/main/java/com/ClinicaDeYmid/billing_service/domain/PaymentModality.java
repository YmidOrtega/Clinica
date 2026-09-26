package com.ClinicaDeYmid.billing_service.domain;

public enum PaymentModality {

    PACKAGE("01", "Pago individual por caso / Conjunto integral de atenciones / Paquete / Canasta"),
    GLOBAL_BUDGET("02", "Pago global prospectivo"),
    CAPITATION("03", "Pago por capitacion"),
    EVENT("04", "Pago por evento");

    private final String sisproCode;
    private final String label;

    PaymentModality(String sisproCode, String label) {
        this.sisproCode = sisproCode;
        this.label = label;
    }

    public String sisproCode() {
        return sisproCode;
    }

    public String label() {
        return label;
    }
}
