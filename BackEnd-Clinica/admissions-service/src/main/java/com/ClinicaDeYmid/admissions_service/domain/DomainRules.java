package com.ClinicaDeYmid.admissions_service.domain;

import java.util.Locale;
import java.util.regex.Pattern;

final class DomainRules {

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private DomainRules() {
    }

    static <T> T required(T value, String field) {
        if (value == null) {
            throw new AdmissionsException.InvalidData(field, "es obligatorio");
        }
        return value;
    }

    static String requiredText(String value, String field, int maxLength) {
        String text = optionalText(value, field, maxLength);
        if (text == null) {
            throw new AdmissionsException.InvalidData(field, "es obligatorio");
        }
        return text;
    }

    static String optionalText(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String text = WHITESPACE.matcher(value.strip()).replaceAll(" ");
        if (text.length() > maxLength) {
            throw new AdmissionsException.InvalidData(field, "no puede superar " + maxLength + " caracteres");
        }
        return text;
    }

    static String upper(String value) {
        return value == null ? null : value.toUpperCase(Locale.ROOT);
    }
}
