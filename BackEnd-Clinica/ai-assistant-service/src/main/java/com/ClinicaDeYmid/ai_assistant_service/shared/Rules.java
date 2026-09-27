package com.ClinicaDeYmid.ai_assistant_service.shared;

import java.util.regex.Pattern;

public final class Rules {

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private Rules() {
    }

    public static <T> T required(T value, String field) {
        if (value == null) {
            throw new AssistantException.InvalidData(field, "es obligatorio");
        }
        return value;
    }

    public static String requiredText(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new AssistantException.InvalidData(field, "es obligatorio");
        }
        String text = WHITESPACE.matcher(value.strip()).replaceAll(" ");
        if (text.length() > maxLength) {
            throw new AssistantException.InvalidData(field, "no puede superar " + maxLength + " caracteres");
        }
        return text;
    }
}
