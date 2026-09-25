package com.ClinicaDeYmid.billing_service.domain;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

public enum TaxResponsibility {

    LARGE_TAXPAYER("O-13"),
    SELF_WITHHOLDER("O-15"),
    VAT_WITHHOLDING_AGENT("O-23"),
    SIMPLE_TAX_REGIME("O-47"),
    NOT_RESPONSIBLE("R-99-PN");

    private final String dianCode;

    TaxResponsibility(String dianCode) {
        this.dianCode = dianCode;
    }

    public String dianCode() {
        return dianCode;
    }

    public static TaxResponsibility ofDianCode(String code) {
        return Arrays.stream(values())
                .filter(responsibility -> responsibility.dianCode.equals(code))
                .findFirst()
                .orElseThrow(() -> new BillingException.InvalidData("taxResponsibilities",
                        "contiene un código que la DIAN no reconoce: " + code));
    }

    public static Set<TaxResponsibility> validated(Set<TaxResponsibility> responsibilities) {
        if (responsibilities == null || responsibilities.isEmpty()) {
            throw new BillingException.InvalidData("taxResponsibilities", "debe tener al menos una responsabilidad fiscal");
        }
        if (responsibilities.contains(NOT_RESPONSIBLE) && responsibilities.size() > 1) {
            throw new BillingException.InvalidData("taxResponsibilities",
                    "no puede combinar R-99-PN (no responsable) con otras responsabilidades");
        }
        return Set.copyOf(EnumSet.copyOf(responsibilities));
    }

    public static String joined(Set<TaxResponsibility> responsibilities) {
        return EnumSet.copyOf(responsibilities).stream().map(TaxResponsibility::dianCode).collect(Collectors.joining(";"));
    }
}
