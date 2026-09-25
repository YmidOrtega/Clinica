package com.ClinicaDeYmid.billing_service.domain;

public enum TaxScheme {

    VAT("01", "IVA"),
    NOT_APPLICABLE("ZZ", "No aplica");

    private final String dianCode;
    private final String dianName;

    TaxScheme(String dianCode, String dianName) {
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
