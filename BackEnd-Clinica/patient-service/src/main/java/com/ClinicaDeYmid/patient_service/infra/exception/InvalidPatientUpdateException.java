package com.ClinicaDeYmid.patient_service.infra.exception;

public class InvalidPatientUpdateException extends RuntimeException {
    private final String field;
    private final String reason;

    public InvalidPatientUpdateException(String field, String reason) {
        super("Error al actualizar el campo '" + field + "': " + reason);
        this.field = field;
        this.reason = reason;
    }

    public String getField() {
        return field;
    }

    public String getReason() {
        return reason;
    }
}
