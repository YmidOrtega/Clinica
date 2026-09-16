package com.ClinicaDeYmid.contracting_service.domain;

import java.util.Locale;
import java.util.regex.Pattern;

final class DomainRules {

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private DomainRules() {
    }

    static <T> T required(T value, String field) {
        if (value == null) {
            throw new ContractingException.InvalidData(field, "es obligatorio");
        }
        return value;
    }

    static String requiredText(String value, String field, int maxLength) {
        String text = optionalText(value, field, maxLength);
        if (text == null) {
            throw new ContractingException.InvalidData(field, "es obligatorio");
        }
        return text;
    }

    static String optionalText(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String text = WHITESPACE.matcher(value.strip()).replaceAll(" ");
        if (text.length() > maxLength) {
            throw new ContractingException.InvalidData(field, "no puede superar " + maxLength + " caracteres");
        }
        return text;
    }

    static String matching(String value, Pattern pattern, String field) {
        if (value != null && !pattern.matcher(value).matches()) {
            throw new ContractingException.InvalidData(field, "no tiene un formato válido");
        }
        return value;
    }

    static String upper(String value) {
        return value == null ? null : value.toUpperCase(Locale.ROOT);
    }

    static String lower(String value) {
        return value == null ? null : value.toLowerCase(Locale.ROOT);
    }
}
