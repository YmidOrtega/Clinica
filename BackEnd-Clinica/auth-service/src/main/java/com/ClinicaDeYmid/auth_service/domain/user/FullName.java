package com.ClinicaDeYmid.auth_service.domain.user;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

@Embeddable
public class FullName {

    public static final int MAX_LENGTH = 150;

    private static final Pattern FORMAT = Pattern.compile("^\\p{L}[\\p{L}\\p{M} '.-]*$");

    @Column(name = "full_name", nullable = false, length = MAX_LENGTH)
    private String value;

    protected FullName() {
    }

    public FullName(String value) {
        String text = DomainRules.requiredText(value, "fullName", 3, MAX_LENGTH);
        if (!FORMAT.matcher(text).matches()) {
            throw new UserException.InvalidData("fullName", "solo admite letras, espacios, apóstrofos, puntos y guiones");
        }
        this.value = text;
    }

    public String value() {
        return value;
    }

    public List<String> words() {
        return Arrays.stream(value.toLowerCase(Locale.ROOT).split("[ '.-]+")).filter(word -> !word.isEmpty()).toList();
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof FullName name && Objects.equals(value, name.value);
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
