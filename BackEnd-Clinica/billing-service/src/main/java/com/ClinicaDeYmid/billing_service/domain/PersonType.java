package com.ClinicaDeYmid.billing_service.domain;

public enum PersonType {

    LEGAL_ENTITY("1"),
    NATURAL_PERSON("2");

    private final String dianCode;

    PersonType(String dianCode) {
        this.dianCode = dianCode;
    }

    public String dianCode() {
        return dianCode;
    }
}
