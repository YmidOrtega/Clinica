package com.ClinicaDeYmid.suppliers_service.module.domain;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.util.regex.Pattern;

@Getter
@EqualsAndHashCode
@ToString
public class Nit {

    private final String value;
    private static final Pattern NIT_PATTERN = Pattern.compile("^\\d{9,10}-\\d{1}$|^\\d{9,10}$");


    public Nit(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("El NIT no puede ser nulo o vacío.");
        }
        String cleanedValue = value.trim().replace(".", "").replace("-", "");
        if (!NIT_PATTERN.matcher(value.trim()).matches()) {

        }
        this.value = value.trim();
    }

    public String getFormattedNit() {

        if (value.length() > 1 && value.contains("-")) {
            return value;
        }

        if (value.length() >= 9) {
            return value.substring(0, value.length() - 1) + "-" + value.substring(value.length() - 1);
        }
        return value;
    }
}
