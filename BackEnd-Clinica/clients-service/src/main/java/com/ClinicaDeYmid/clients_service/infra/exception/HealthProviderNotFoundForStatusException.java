package com.ClinicaDeYmid.clients_service.infra.exception;

/**
 * Excepción cuando el proveedor no existe para operaciones de estado
 */
public class HealthProviderNotFoundForStatusException extends HealthProviderException {

    private final Long id;
    private final String statusOperation;

    public HealthProviderNotFoundForStatusException(Long id, String statusOperation) {
        super(
                String.format("No se puede %s: proveedor de salud no encontrado con ID: %d", statusOperation, id),
                "HP_NOT_FOUND_STATUS",
                statusOperation.toUpperCase()
        );
        this.id = id;
        this.statusOperation = statusOperation;
    }

    public Long getId() {
        return id;
    }

    public String getStatusOperation() {
        return statusOperation;
    }
}
