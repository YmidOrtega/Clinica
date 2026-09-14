package com.ClinicaDeYmid.clinical_history_service.domain;

import java.util.regex.Pattern;

public final class ClinicalText {

    public static final int SHORT = 500;
    public static final int LONG = 20_000;

    private static final Pattern TRAILING_SPACES = Pattern.compile("[ \\t]+$", Pattern.MULTILINE);

    private ClinicalText() {
    }

    public static String required(String value, String field, int maxLength) {
        String text = optional(value, field, maxLength);
        if (text == null) {
            throw new ClinicalException.InvalidData(field, "es obligatorio");
        }
        return text;
    }

    public static String optional(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String text = TRAILING_SPACES.matcher(value.strip()).replaceAll("");
        if (text.length() > maxLength) {
            throw new ClinicalException.InvalidData(field, "no puede superar " + maxLength + " caracteres");
        }
        return text;
    }

    public static <T> T present(T value, String field) {
        if (value == null) {
            throw new ClinicalException.InvalidData(field, "es obligatorio");
        }
        return value;
    }
}
