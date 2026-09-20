package com.ClinicaDeYmid.practitioners_service.repository.entity;

import com.ClinicaDeYmid.practitioners_service.shared.Rules;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.util.Objects;
import java.util.regex.Pattern;

@Embeddable
public class ContactInfo {

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s.]+(\\.[^@\\s.]+)+$");
    private static final Pattern MOBILE = Pattern.compile("^3[0-9]{9}$");
    private static final Pattern PHONE = Pattern.compile("^[0-9]{7,12}$");

    @Column(name = "email", nullable = false, length = 150)
    private String email;

    @Column(name = "mobile", nullable = false, length = 20)
    private String mobile;

    @Column(name = "phone", length = 20)
    private String phone;

    protected ContactInfo() {
    }

    public ContactInfo(String email, String mobile, String phone) {
        this.email = Rules.matching(Rules.lower(Rules.requiredText(email, "contact.email", 150)), EMAIL, "contact.email");
        this.mobile = Rules.matching(Rules.requiredText(mobile, "contact.mobile", 20), MOBILE, "contact.mobile");
        this.phone = Rules.matching(Rules.optionalText(phone, "contact.phone", 20), PHONE, "contact.phone");
    }

    public String email() {
        return email;
    }

    public String mobile() {
        return mobile;
    }

    public String phone() {
        return phone;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof ContactInfo contact && Objects.equals(email, contact.email)
                && Objects.equals(mobile, contact.mobile) && Objects.equals(phone, contact.phone);
    }

    @Override
    public int hashCode() {
        return Objects.hash(email, mobile, phone);
    }
}
