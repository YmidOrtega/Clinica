package com.ClinicaDeYmid.billing_service.domain;

import java.util.UUID;

public record HealthUser(UUID patientUuid, String documentType, String documentNumber, String name,
                         String healthRegime) {

    public HealthUser {
        DomainRules.required(patientUuid, "patientUuid");
        documentType = DomainRules.requiredText(documentType, "documentType", 40);
        documentNumber = DomainRules.requiredText(documentNumber, "documentNumber", 20);
        name = DomainRules.requiredText(name, "name", 200);
        healthRegime = DomainRules.optionalText(healthRegime, "healthRegime", 40);
    }
}
