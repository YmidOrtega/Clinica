package com.ClinicaDeYmid.contracting_service.domain;

import jakarta.persistence.Embeddable;

import java.util.Objects;
import java.util.regex.Pattern;

@Embeddable
public class ContactInfo {

    private static final Pattern PHONE = Pattern.compile("^\\+?[0-9]{7,15}$");
    private static final Pattern PHONE_SEPARATORS = Pattern.compile("[\\s()-]");
    private static final Pattern EMAIL = Pattern.compile("^[a-z0-9.!#$%&'*+/=?^_`{|}~-]{1,64}@[a-z0-9-]+([.][a-z0-9-]+)+$");

    private String address;
    private String phone;
    private String billingEmail;

    protected ContactInfo() {
    }

    public ContactInfo(String address, String phone, String billingEmail) {
        this.address = DomainRules.requiredText(address, "contact.address", 255);
        String digits = DomainRules.requiredText(phone, "contact.phone", 20);
        this.phone = DomainRules.matching(PHONE_SEPARATORS.matcher(digits).replaceAll(""), PHONE, "contact.phone");
        this.billingEmail = DomainRules.matching(
                DomainRules.lower(DomainRules.optionalText(billingEmail, "contact.billingEmail", 150)), EMAIL, "contact.billingEmail");
    }

    public String address() {
        return address;
    }

    public String phone() {
        return phone;
    }

    public String billingEmail() {
        return billingEmail;
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof ContactInfo that
                && Objects.equals(address, that.address) && Objects.equals(phone, that.phone)
                && Objects.equals(billingEmail, that.billingEmail);
    }

    @Override
    public int hashCode() {
        return Objects.hash(address, phone, billingEmail);
    }
}
