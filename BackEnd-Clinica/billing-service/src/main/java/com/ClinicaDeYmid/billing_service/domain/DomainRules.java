package com.ClinicaDeYmid.billing_service.domain;

import java.util.Locale;
import java.util.regex.Pattern;

final class DomainRules {

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private DomainRules() {
    }

    static <T> T required(T value, String field) {
        if (value == null) {
            throw new BillingException.InvalidData(field, "es obligatorio");
        }
        return value;
    }

    static String requiredText(String value, String field, int maxLength) {
        String text = optionalText(value, field, maxLength);
        if (text == null) {
            throw new BillingException.InvalidData(field, "es obligatorio");
        }
        return text;
    }

    static String optionalText(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String text = WHITESPACE.matcher(value.strip()).replaceAll(" ");
        if (text.length() > maxLength) {
            throw new BillingException.InvalidData(field, "no puede superar " + maxLength + " caracteres");
        }
        return text;
    }

    static String requiredPattern(String value, String field, Pattern pattern, String expectation) {
        String text = required(value, field).strip();
        if (!pattern.matcher(text).matches()) {
            throw new BillingException.InvalidData(field, expectation);
        }
        return text;
    }

    static String optionalPattern(String value, String field, Pattern pattern, String expectation) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return requiredPattern(value, field, pattern, expectation);
    }

    static String email(String value, String field) {
        return requiredPattern(requiredText(value, field, 254).toLowerCase(Locale.ROOT), field, EMAIL,
                "debe ser un correo electrónico válido");
    }
}
