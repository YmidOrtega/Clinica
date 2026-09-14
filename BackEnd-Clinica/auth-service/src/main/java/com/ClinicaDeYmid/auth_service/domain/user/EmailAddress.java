package com.ClinicaDeYmid.auth_service.domain.user;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

@Embeddable
public class EmailAddress {

    public static final int MAX_LENGTH = 254;

    private static final Pattern FORMAT = Pattern.compile("^[a-z0-9.!#$%&'*+/=?^_`{|}~-]{1,64}@[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?(?:\\.[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?)+$");

    @Column(name = "email", nullable = false, length = MAX_LENGTH)
    private String value;

    protected EmailAddress() {
    }

    public EmailAddress(String value) {
        if (value == null || value.isBlank()) {
            throw new UserException.InvalidData("email", "es obligatorio");
        }
        String normalized = value.strip().toLowerCase(Locale.ROOT);
        if (normalized.length() > MAX_LENGTH || !FORMAT.matcher(normalized).matches()) {
            throw new UserException.InvalidData("email", "no tiene un formato válido");
        }
        this.value = normalized;
    }

    public String value() {
        return value;
    }

    public String localPart() {
        return value.substring(0, value.indexOf('@'));
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof EmailAddress email && Objects.equals(value, email.value);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
