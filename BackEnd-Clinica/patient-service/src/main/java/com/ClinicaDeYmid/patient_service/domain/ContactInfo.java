package com.ClinicaDeYmid.patient_service.domain;

import jakarta.persistence.Embeddable;

import java.util.Objects;
import java.util.regex.Pattern;

@Embeddable
public class ContactInfo {

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private String mobile;
    private String phone;
    private String email;

    protected ContactInfo() {
    }

    public ContactInfo(String mobile, String phone, String email) {
        this.mobile = DomainRules.phone(mobile, "contact.mobile", true);
        this.phone = DomainRules.phone(phone, "contact.phone", false);
        this.email = DomainRules.matching(
                DomainRules.lower(DomainRules.optionalText(email, "contact.email", 150)), EMAIL, "contact.email");
    }

    public String mobile() {
        return mobile;
    }

    public String phone() {
        return phone;
    }

    public String email() {
        return email;
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof ContactInfo that && Objects.equals(mobile, that.mobile)
                && Objects.equals(phone, that.phone) && Objects.equals(email, that.email);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mobile, phone, email);
    }

    @Override
    public String toString() {
        return "ContactInfo[redacted]";
    }
}
