package com.ClinicaDeYmid.patient_service.domain;

import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

import java.util.Objects;

@Embeddable
public class EmergencyContact {

    private String fullName;

    @Enumerated(EnumType.STRING)
    private Relationship relationship;

    private String phone;

    protected EmergencyContact() {
    }

    public EmergencyContact(String fullName, Relationship relationship, String phone) {
        this.fullName = DomainRules.personName(fullName, "emergencyContact.fullName", 150);
        this.relationship = DomainRules.required(relationship, "emergencyContact.relationship");
        this.phone = DomainRules.phone(phone, "emergencyContact.phone", true);
    }

    public String fullName() {
        return fullName;
    }

    public Relationship relationship() {
        return relationship;
    }

    public String phone() {
        return phone;
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof EmergencyContact that && Objects.equals(fullName, that.fullName)
                && relationship == that.relationship && Objects.equals(phone, that.phone);
    }

    @Override
    public int hashCode() {
        return Objects.hash(fullName, relationship, phone);
    }

    @Override
    public String toString() {
        return "EmergencyContact[redacted]";
    }
}
