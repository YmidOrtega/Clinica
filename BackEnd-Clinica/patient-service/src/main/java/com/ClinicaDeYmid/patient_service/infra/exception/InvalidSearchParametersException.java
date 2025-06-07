package com.ClinicaDeYmid.patient_service.infra.exception;

public class InvalidSearchParametersException extends RuntimeException {
    private final String parameter;
    private final String value;

    public InvalidSearchParametersException(String parameter, String value) {
        super("Parámetro de búsqueda inválido - " + parameter + ": " + value);
        this.parameter = parameter;
        this.value = value;
    }

    public String getParameter() {
        return parameter;
    }

    public String getValue() {
        return value;
    }
}
