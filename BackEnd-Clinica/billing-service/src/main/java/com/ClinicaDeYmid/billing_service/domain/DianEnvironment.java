package com.ClinicaDeYmid.billing_service.domain;

public enum DianEnvironment {

    TEST("2"),
    PRODUCTION("1");

    private final String dianCode;

    DianEnvironment(String dianCode) {
        this.dianCode = dianCode;
    }

    public String dianCode() {
        return dianCode;
    }
}
