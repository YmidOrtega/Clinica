package com.ClinicaDeYmid.billing_service.domain;

public enum DianEnvironment {

    TEST("2", "https://catalogo-vpfe-hab.dian.gov.co/document/searchqr?documentkey="),
    PRODUCTION("1", "https://catalogo-vpfe.dian.gov.co/document/searchqr?documentkey=");

    private final String dianCode;
    private final String searchUrl;

    DianEnvironment(String dianCode, String searchUrl) {
        this.dianCode = dianCode;
        this.searchUrl = searchUrl;
    }

    public String searchUrlOf(String cufe) {
        return searchUrl + cufe;
    }

    public String dianCode() {
        return dianCode;
    }
}
