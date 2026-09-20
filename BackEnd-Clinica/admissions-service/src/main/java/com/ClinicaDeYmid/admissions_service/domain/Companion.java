package com.ClinicaDeYmid.admissions_service.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public class Companion {

    @Column(name = "companion_full_name", length = 160)
    private String fullName;

    @Column(name = "companion_phone_number", length = 30)
    private String phoneNumber;

    @Column(name = "companion_relationship", length = 60)
    private String relationship;

    protected Companion() {
    }

    public static Companion of(String fullName, String phoneNumber, String relationship) {
        Companion companion = new Companion();
        companion.fullName = DomainRules.requiredText(fullName, "companion.fullName", 160);
        companion.phoneNumber = DomainRules.requiredText(phoneNumber, "companion.phoneNumber", 30);
        companion.relationship = DomainRules.optionalText(relationship, "companion.relationship", 60);
        return companion;
    }

    public String fullName() {
        return fullName;
    }

    public String phoneNumber() {
        return phoneNumber;
    }

    public String relationship() {
        return relationship;
    }
}
