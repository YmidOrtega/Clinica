package com.ClinicaDeYmid.contracting_service.domain;

public record PayerRegistration(String socialReason, Nit nit, PayerType type, String adresCode, ContactInfo contact) {

    public PayerRegistration {
        socialReason = DomainRules.requiredText(socialReason, "socialReason", 200);
        DomainRules.required(nit, "nit");
        DomainRules.required(type, "type");
        adresCode = DomainRules.upper(DomainRules.optionalText(adresCode, "adresCode", 20));
        DomainRules.required(contact, "contact");
    }
}
