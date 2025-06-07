package com.ClinicaDeYmid.patient_service.infra.exception;

public class PatientSearchNoResultsException extends RuntimeException {
    private final String query;

    public PatientSearchNoResultsException(String query) {
        super("No se encontraron pacientes para la búsqueda: " + query);
        this.query = query;
    }

    public String getQuery() {
        return query;
    }
}
