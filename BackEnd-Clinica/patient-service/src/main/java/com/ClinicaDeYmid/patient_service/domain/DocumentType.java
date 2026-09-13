package com.ClinicaDeYmid.patient_service.domain;

import java.util.regex.Pattern;

public enum DocumentType {

    REGISTRO_CIVIL("Registro Civil", Format.NUMERIC, 0, 7),
    TARJETA_DE_IDENTIDAD("Tarjeta de Identidad", Format.NUMERIC, 7, 18),
    CEDULA_DE_CIUDADANIA("Cédula de Ciudadanía", Format.NUMERIC, 18, null),
    CEDULA_DE_EXTRANJERIA("Cédula de Extranjería", Format.ALPHANUMERIC, null, null),
    PASAPORTE("Pasaporte", Format.ALPHANUMERIC, null, null),
    PERMISO_ESPECIAL_DE_PERMANENCIA("Permiso Especial de Permanencia", Format.NUMERIC, null, null),
    PERMISO_POR_PROTECCION_TEMPORAL("Permiso por Protección Temporal", Format.NUMERIC, null, null),
    DOCUMENTO_EXTRANJERO("Documento extranjero", Format.ALPHANUMERIC, null, null);

    private final String label;
    private final Format format;
    private final Integer minAge;
    private final Integer maxAgeExclusive;

    DocumentType(String label, Format format, Integer minAge, Integer maxAgeExclusive) {
        this.label = label;
        this.format = format;
        this.minAge = minAge;
        this.maxAgeExclusive = maxAgeExclusive;
    }

    public String label() {
        return label;
    }

    public boolean acceptsNumber(String number) {
        return format.pattern.matcher(number).matches();
    }

    public boolean acceptsAge(int years) {
        return (minAge == null || years >= minAge) && (maxAgeExclusive == null || years < maxAgeExclusive);
    }

    private enum Format {
        NUMERIC("^[0-9]{3,15}$"),
        ALPHANUMERIC("^[A-Z0-9]{3,20}$");

        private final Pattern pattern;

        Format(String regex) {
            this.pattern = Pattern.compile(regex);
        }
    }
}
