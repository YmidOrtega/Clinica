package com.ClinicaDeYmid.patient_service.domain;

import java.util.Locale;
import java.util.regex.Pattern;

final class DomainRules {

    static final Pattern PERSON_NAME = Pattern.compile("^\\p{L}[\\p{L} '.-]*$");
    static final Pattern PHONE = Pattern.compile("^\\+?[0-9]{7,15}$");

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final Pattern PHONE_SEPARATORS = Pattern.compile("[\\s()-]");

    private DomainRules() {
    }

    static <T> T required(T value, String field) {
        if (value == null) {
            throw new PatientException.InvalidData(field, "es obligatorio");
        }
        return value;
    }

    static String requiredText(String value, String field, int maxLength) {
        String text = optionalText(value, field, maxLength);
        if (text == null) {
            throw new PatientException.InvalidData(field, "es obligatorio");
        }
        return text;
    }

    static String optionalText(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String text = WHITESPACE.matcher(value.strip()).replaceAll(" ");
        if (text.length() > maxLength) {
            throw new PatientException.InvalidData(field, "no puede superar " + maxLength + " caracteres");
        }
        return text;
    }

    static String matching(String value, Pattern pattern, String field) {
        if (value != null && !pattern.matcher(value).matches()) {
            throw new PatientException.InvalidData(field, "no tiene un formato válido");
        }
        return value;
    }

    static String personName(String value, String field, int maxLength) {
        return matching(requiredText(value, field, maxLength), PERSON_NAME, field);
    }

    static String phone(String value, String field, boolean required) {
        String text = required ? requiredText(value, field, 20) : optionalText(value, field, 20);
        return text == null ? null : matching(PHONE_SEPARATORS.matcher(text).replaceAll(""), PHONE, field);
    }

    static String upper(String value) {
        return value == null ? null : value.toUpperCase(Locale.ROOT);
    }

    static String lower(String value) {
        return value == null ? null : value.toLowerCase(Locale.ROOT);
    }
}
