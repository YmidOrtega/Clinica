package com.ClinicaDeYmid.practitioners_service.shared;

import java.util.Locale;
import java.util.regex.Pattern;

public final class Rules {

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private Rules() {
    }

    public static <T> T required(T value, String field) {
        if (value == null) {
            throw new PractitionersException.InvalidData(field, "es obligatorio");
        }
        return value;
    }

    public static String requiredText(String value, String field, int maxLength) {
        String text = optionalText(value, field, maxLength);
        if (text == null) {
            throw new PractitionersException.InvalidData(field, "es obligatorio");
        }
        return text;
    }

    public static String optionalText(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String text = WHITESPACE.matcher(value.strip()).replaceAll(" ");
        if (text.length() > maxLength) {
            throw new PractitionersException.InvalidData(field, "no puede superar " + maxLength + " caracteres");
        }
        return text;
    }

    public static String matching(String value, Pattern pattern, String field) {
        if (value != null && !pattern.matcher(value).matches()) {
            throw new PractitionersException.InvalidData(field, "no tiene un formato válido");
        }
        return value;
    }

    public static String upper(String value) {
        return value == null ? null : value.toUpperCase(Locale.ROOT);
    }

    public static String atLeast(String value, int minLength, String field) {
        if (value != null && value.length() < minLength) {
            throw new PractitionersException.InvalidData(field, "debe tener al menos " + minLength + " caracteres");
        }
        return value;
    }
}
