package com.ClinicaDeYmid.auth_service.domain.user;

import java.util.regex.Pattern;

final class DomainRules {

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private DomainRules() {
    }

    static <T> T required(T value, String field) {
        if (value == null) {
            throw new UserException.InvalidData(field, "es obligatorio");
        }
        return value;
    }

    static String requiredText(String value, String field, int minLength, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new UserException.InvalidData(field, "es obligatorio");
        }
        String text = WHITESPACE.matcher(value.strip()).replaceAll(" ");
        if (text.codePointCount(0, text.length()) < minLength) {
            throw new UserException.InvalidData(field, "debe tener al menos " + minLength + " caracteres");
        }
        if (text.codePointCount(0, text.length()) > maxLength) {
            throw new UserException.InvalidData(field, "no puede superar " + maxLength + " caracteres");
        }
        return text;
    }
}
