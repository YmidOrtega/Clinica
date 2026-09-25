package com.ClinicaDeYmid.billing_service.domain;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

public record FeeAgreementTerms(UUID agreementUuid, String basis, Map<String, BigDecimal> perProcedure) {

    public static final String PER_PROCEDURE = "PER_PROCEDURE";

    public FeeAgreementTerms {
        DomainRules.required(agreementUuid, "agreementUuid");
        DomainRules.required(basis, "basis");
        perProcedure = perProcedure == null ? Map.of() : Map.copyOf(perProcedure);
    }
}
