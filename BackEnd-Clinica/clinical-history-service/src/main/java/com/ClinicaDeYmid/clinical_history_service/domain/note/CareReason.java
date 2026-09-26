package com.ClinicaDeYmid.clinical_history_service.domain.note;

import com.ClinicaDeYmid.clinical_history_service.domain.ClinicalException;

import java.util.regex.Pattern;

public record CareReason(String purpose, String cause) {

    private static final Pattern CODE = Pattern.compile("^[0-9]{2}$");

    public CareReason {
        purpose = optional(purpose, "careReason.purpose");
        cause = optional(cause, "careReason.cause");
        if (purpose == null && cause == null) {
            throw new ClinicalException.InvalidData("careReason", "debe indicar la finalidad o la causa de la atención");
        }
    }

    private static String optional(String value, String field) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.strip();
        if (!CODE.matcher(trimmed).matches()) {
            throw new ClinicalException.InvalidData(field, "debe ser un código de dos dígitos de las tablas de referencia de RIPS");
        }
        return trimmed;
    }
}
