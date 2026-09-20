package com.ClinicaDeYmid.practitioners_service.shared;

import java.util.regex.Pattern;

public enum DocumentType {

    CEDULA_DE_CIUDADANIA("Cédula de Ciudadanía", Pattern.compile("^[0-9]{4,15}$")),
    CEDULA_DE_EXTRANJERIA("Cédula de Extranjería", Pattern.compile("^[A-Z0-9]{4,15}$")),
    PASAPORTE("Pasaporte", Pattern.compile("^[A-Z0-9]{5,15}$")),
    PERMISO_ESPECIAL_DE_PERMANENCIA("Permiso Especial de Permanencia", Pattern.compile("^[0-9]{5,15}$")),
    PERMISO_POR_PROTECCION_TEMPORAL("Permiso por Protección Temporal", Pattern.compile("^[0-9]{5,15}$")),
    DOCUMENTO_EXTRANJERO("Documento extranjero", Pattern.compile("^[A-Z0-9-]{5,20}$"));

    private final String label;
    private final Pattern format;

    DocumentType(String label, Pattern format) {
        this.label = label;
        this.format = format;
    }

    public String label() {
        return label;
    }

    public Pattern format() {
        return format;
    }
}
