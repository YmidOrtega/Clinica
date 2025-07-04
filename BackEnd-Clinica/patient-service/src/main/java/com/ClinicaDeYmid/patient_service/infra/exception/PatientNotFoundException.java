package com.ClinicaDeYmid.patient_service.infra.exception;

public class PatientNotFoundException extends RuntimeException {
    private final String identificationNumber;

    public PatientNotFoundException(String identificationNumber) {
        super("Paciente no encontrado con identificación: " + identificationNumber);
        this.identificationNumber = identificationNumber;
    }

    public String getIdentificationNumber() {
        return identificationNumber;
    }
}
