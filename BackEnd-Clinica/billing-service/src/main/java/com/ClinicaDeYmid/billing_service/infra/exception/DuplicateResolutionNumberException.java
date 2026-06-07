package com.ClinicaDeYmid.billing_service.infra.exception;

public class DuplicateResolutionNumberException extends RuntimeException {
    public DuplicateResolutionNumberException(String resolutionNumber) {
        super("Ya existe una resolución DIAN registrada con el número '" + resolutionNumber + "'.");
    }
}
