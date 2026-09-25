package com.ClinicaDeYmid.billing_service.support;

public final class BillingSetup {

    public static final String ISSUER = "/api/v1/billing/issuer";
    public static final String RESOLUTIONS = "/api/v1/billing/numbering-resolutions";
    public static final String DIAN_TEST_KEY = "fc8eac422eba16e22ffd8c6f94b3f40a6e38162c";

    private BillingSetup() {
    }

    public static String profile() {
        return profile("Clínica de Ymid S.A.S.");
    }

    public static String profile(String legalName) {
        return """
                {"personType":"LEGAL_ENTITY","legalName":"%s","tradeName":"Clínica de Ymid",
                 "taxScheme":"NOT_APPLICABLE","taxResponsibilities":["LARGE_TAXPAYER","SELF_WITHHOLDER"],
                 "addressLine":"Calle 10 # 43-20","municipalityCode":"05001","cityName":"Medellín",
                 "departmentName":"Antioquia","postalCode":"050021","email":"facturacion@clinica.co",
                 "phone":"6044441234","healthProviderCode":"050010123401"}""".formatted(legalName);
    }

    public static String configuration(String nit, int verificationDigit) {
        return """
                {"nit":"%s","verificationDigit":%d,"profile":%s}""".formatted(nit, verificationDigit, profile());
    }

    public static String configuration() {
        return configuration("800197268", 4);
    }

    public static String resolution(String number, String prefix, long from, long to, String validFrom,
                                    String validUntil) {
        return """
                {"resolutionNumber":"%s","issuedOn":"2026-01-01","prefix":"%s","rangeFrom":%d,"rangeTo":%d,
                 "validFrom":"%s","validUntil":"%s","technicalKey":"%s"}"""
                .formatted(number, prefix, from, to, validFrom, validUntil, DIAN_TEST_KEY);
    }

    public static String resolution(String number, String prefix, long from, long to) {
        return resolution(number, prefix, from, to, "2026-01-01", "2030-01-19");
    }
}
