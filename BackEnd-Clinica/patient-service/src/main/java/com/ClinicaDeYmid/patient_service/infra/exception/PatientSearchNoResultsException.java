package com.ClinicaDeYmid.patient_service.infra.exception;

import lombok.Getter;

@Getter
public class PatientSearchNoResultsException extends RuntimeException {

    private final String searchCriteria;

    public PatientSearchNoResultsException(String searchCriteria) {
        super("No se encontraron pacientes para: " + searchCriteria);
        this.searchCriteria = searchCriteria;
    }

}

