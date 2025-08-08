package com.ClinicaDeYmid.clients_service.module.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;


@Getter
@EqualsAndHashCode
@ToString
public class Nit {

    private final String value;

    protected Nit() {
        this.value = null;
    }

    @JsonCreator
    public Nit(String rawValue) {
        if (rawValue == null || rawValue.trim().isEmpty()) {
            throw new IllegalArgumentException("El NIT no puede ser nulo o vacío.");
        }

        String cleanedValue = rawValue.trim().replace(".", "").replace("-", "");

        if (!cleanedValue.matches("^\\d{9,11}$")) {
            throw new IllegalArgumentException("Formato de NIT inválido.");
        }

        this.value = cleanedValue;
    }

    public String getFormattedNit() {
        if (value == null) {
            return null;
        }
        if (value.length() >= 2) {
            return value.substring(0, value.length() - 1) + "-" + value.charAt(value.length() - 1);
        }
        return value;
    }

    @JsonValue
    public String asJson() {
        return getFormattedNit();
    }
}