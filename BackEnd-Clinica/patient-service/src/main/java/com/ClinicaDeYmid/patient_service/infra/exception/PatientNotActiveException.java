package com.ClinicaDeYmid.patient_service.infra.exception;

public class PatientNotActiveException extends RuntimeException {
    private final String status;

    public PatientNotActiveException(String status) {
        super("El paciente no está activo (estado: " + status + ")");
        this.status = status;
    }

    public String getStatus() {
        return status;
    }
}
