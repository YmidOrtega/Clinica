package com.ClinicaDeYmid.billing_service.domain;

import java.util.UUID;

public record Buyer(Kind kind, UUID reference, String documentType, String documentNumber, String name) {

    public enum Kind {
        PAYER,
        PATIENT
    }

    public Buyer {
        DomainRules.required(kind, "kind");
        DomainRules.required(reference, "reference");
        documentType = DomainRules.requiredText(documentType, "documentType", 40);
        documentNumber = DomainRules.requiredText(documentNumber, "documentNumber", 20);
        name = DomainRules.requiredText(name, "name", 200);
    }
}
