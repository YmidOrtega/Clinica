package com.ClinicaDeYmid.patient_service.infra.exception;

public class PatientDataAccessException extends RuntimeException {
    private final String operation;

    public PatientDataAccessException(String operation, Throwable cause) {
        super("Error de acceso a datos durante la operación: " + operation, cause);
        this.operation = operation;
    }

    public String getOperation() {
        return operation;
    }
}