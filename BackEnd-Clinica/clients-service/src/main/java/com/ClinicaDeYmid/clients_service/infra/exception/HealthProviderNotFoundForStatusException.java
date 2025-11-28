package com.ClinicaDeYmid.clients_service.infra.exception;

import lombok.Getter;

/**
 * Excepción cuando el proveedor no existe para operaciones de estado
 */
public class HealthProviderNotFoundForStatusException extends HealthProviderException {

    @Getter
    private final String nit;
    private final String operation;

    /**
     * Constructor con NIT
     */
    public HealthProviderNotFoundForStatusException(String nit, String operation) {
        super(
                String.format("Proveedor de salud con NIT '%s' no encontrado para la operación: %s", nit, operation),
                "HP_NOT_FOUND",
                operation
        );
        this.nit = nit;
        this.operation = operation;
    }

    /**
     * Constructor con ID (LEGACY - mantener por compatibilidad)
     * @deprecated Usar constructor con NIT
     */
    @Deprecated
    public HealthProviderNotFoundForStatusException(Long id, String operation) {
        super(
                String.format("Proveedor de salud con ID %d no encontrado para la operación: %s", id, operation),
                "HP_NOT_FOUND",
                operation
        );
        this.nit = null;
        this.operation = operation;
    }

    @Override
    public String getOperation() {
        return operation;
    }
}
