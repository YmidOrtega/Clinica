package com.ClinicaDeYmid.admissions_service.infra.exception;

public class AttentionSearchException extends RuntimeException {
    private final String searchCriteria;

    public AttentionSearchException(String message, String searchCriteria) {
        super(message);
        this.searchCriteria = searchCriteria;
    }

    public String getSearchCriteria() {
        return searchCriteria;
    }
}
