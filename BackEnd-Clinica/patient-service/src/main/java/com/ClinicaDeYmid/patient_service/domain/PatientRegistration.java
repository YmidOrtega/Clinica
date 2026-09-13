package com.ClinicaDeYmid.patient_service.domain;

public record PatientRegistration(
        IdentityDocument document,
        Demographics demographics,
        ContactInfo contact,
        EmergencyContact emergencyContact,
        Affiliation affiliation,
        Residence residence) {

    public PatientRegistration {
        DomainRules.required(document, "document");
        DomainRules.required(demographics, "demographics");
        DomainRules.required(contact, "contact");
        DomainRules.required(affiliation, "affiliation");
        DomainRules.required(residence, "residence");
    }
}
