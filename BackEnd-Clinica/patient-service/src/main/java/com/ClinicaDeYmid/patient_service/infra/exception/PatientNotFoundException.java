package com.ClinicaDeYmid.patient_service.infra.exception;

public class PatientNotFoundException extends RuntimeException {
    private final String identification;

    public PatientNotFoundException(String identification) {
        super("Paciente no encontrado con identificación: " + identification);
        this.identification = identification;
    }

    public String getIdentification() {
        return identification;
    }
}
