package com.ClinicaDeYmid.clients_service.infra.exception;

public class HealthProviderValidationException extends HealthProviderException {
    public HealthProviderValidationException(String message) {
        super("VALIDATION_ERROR", message, "validation");
    }
}