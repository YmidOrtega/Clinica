package com.ClinicaDeYmid.billing_service.domain;

import java.util.Set;
import java.util.regex.Pattern;

public record IssuerProfile(
        PersonType personType,
        String legalName,
        String tradeName,
        TaxScheme taxScheme,
        Set<TaxResponsibility> taxResponsibilities,
        String addressLine,
        String municipalityCode,
        String cityName,
        String departmentName,
        String postalCode,
        String email,
        String phone,
        String healthProviderCode) {

    private static final Pattern MUNICIPALITY = Pattern.compile("^[0-9]{5}$");
    private static final Pattern POSTAL_CODE = Pattern.compile("^[0-9]{6}$");
    private static final Pattern PHONE = Pattern.compile("^[0-9]{7,10}$");
    private static final Pattern HEALTH_PROVIDER = Pattern.compile("^[0-9]{10,12}$");

    public IssuerProfile {
        DomainRules.required(personType, "personType");
        legalName = DomainRules.requiredText(legalName, "legalName", 200);
        tradeName = DomainRules.optionalText(tradeName, "tradeName", 200);
        DomainRules.required(taxScheme, "taxScheme");
        taxResponsibilities = TaxResponsibility.validated(taxResponsibilities);
        addressLine = DomainRules.requiredText(addressLine, "addressLine", 200);
        municipalityCode = DomainRules.requiredPattern(municipalityCode, "municipalityCode", MUNICIPALITY,
                "debe ser el código DANE de 5 dígitos del municipio");
        cityName = DomainRules.requiredText(cityName, "cityName", 60);
        departmentName = DomainRules.requiredText(departmentName, "departmentName", 60);
        postalCode = DomainRules.optionalPattern(postalCode, "postalCode", POSTAL_CODE, "debe tener 6 dígitos");
        email = DomainRules.email(email, "email");
        phone = DomainRules.requiredPattern(phone, "phone", PHONE, "debe tener entre 7 y 10 dígitos");
        healthProviderCode = DomainRules.requiredPattern(healthProviderCode, "healthProviderCode", HEALTH_PROVIDER,
                "debe ser el código de habilitación REPS de 10 a 12 dígitos");
    }

    public String departmentCode() {
        return municipalityCode.substring(0, 2);
    }
}
