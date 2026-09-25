package com.ClinicaDeYmid.billing_service.domain;

import java.util.UUID;
import java.util.regex.Pattern;

public record ChargedService(UUID portfolioItemUuid, String cupsCode, String clinicCode, String description,
                             String category) {

    private static final Pattern CUPS = Pattern.compile("^[0-9]{6,8}$");

    public ChargedService {
        DomainRules.required(portfolioItemUuid, "portfolioItemUuid");
        cupsCode = DomainRules.requiredPattern(cupsCode, "cupsCode", CUPS, "debe ser un código CUPS de 6 a 8 dígitos");
        clinicCode = DomainRules.optionalText(clinicCode, "clinicCode", 20);
        description = DomainRules.requiredText(description, "description", 300);
        category = DomainRules.optionalText(category, "category", 40);
    }
}
