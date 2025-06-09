package com.ClinicaDeYmid.clients_service.infra.exception;

/**
 * Excepción base para todos los errores relacionados con HealthProvider
 */
public abstract class HealthProviderException extends RuntimeException {

    protected final String errorCode;
    protected final String operation;

    protected HealthProviderException(String message, String errorCode, String operation) {
        super(message);
        this.errorCode = errorCode;
        this.operation = operation;
    }

    protected HealthProviderException(String message, String errorCode, String operation, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.operation = operation;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getOperation() {
        return operation;
    }
}
