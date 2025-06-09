package com.ClinicaDeYmid.clients_service.infra.exception;

/**
 * Excepción mejorada para acceso a datos
 */
public class HealthProviderDataAccessException extends HealthProviderException {

    private final String specificOperation;

    public HealthProviderDataAccessException(String specificOperation, Throwable cause) {
        super(
                String.format("Error de acceso a datos durante la operación: %s", specificOperation),
                "HP_DATA_ACCESS_ERROR",
                "DATA_ACCESS",
                cause
        );
        this.specificOperation = specificOperation;
    }

    public String getSpecificOperation() {
        return specificOperation;
    }
}
