package com.ClinicaDeYmid.patient_service.domain;

import java.util.regex.Pattern;

public final class UnidentifiedPatientCode {

    private static final Pattern FORMAT = Pattern.compile("^NN-[0-9]{4}-[0-9]{6}$");
    private static final int MAX_SEQUENCE = 999_999;

    private UnidentifiedPatientCode() {
    }

    public static String of(int year, long sequence) {
        if (sequence < 1 || sequence > MAX_SEQUENCE) {
            throw new IllegalStateException("Unidentified patient sequence out of range for " + year + ": " + sequence);
        }
        return "NN-%04d-%06d".formatted(year, sequence);
    }

    static String validated(String code) {
        return DomainRules.matching(DomainRules.requiredText(code, "code", 14), FORMAT, "code");
    }
}
