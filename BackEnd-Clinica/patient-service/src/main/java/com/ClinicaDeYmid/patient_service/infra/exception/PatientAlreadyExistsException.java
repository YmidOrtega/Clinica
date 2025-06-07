package com.ClinicaDeYmid.patient_service.infra.exception;

public class PatientAlreadyExistsException extends RuntimeException {
    private final String identificationNumber;

    public PatientAlreadyExistsException(String identificationNumber) {
        super("Ya existe un paciente registrado con la identificación: " + identificationNumber);
        this.identificationNumber = identificationNumber;
    }

    public String getIdentificationNumber() {
        return identificationNumber;
    }
}