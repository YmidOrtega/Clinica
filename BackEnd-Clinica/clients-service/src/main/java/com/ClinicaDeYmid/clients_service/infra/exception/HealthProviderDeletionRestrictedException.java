package com.ClinicaDeYmid.clients_service.infra.exception;

/**
 * Excepción cuando no se puede eliminar un proveedor por restricciones
 */
public class HealthProviderDeletionRestrictedException extends HealthProviderException {

    private final Long id;
    private final String reason;

    public HealthProviderDeletionRestrictedException(Long id, String reason) {
        super(
                String.format("No se puede eliminar el proveedor de salud con ID %d: %s", id, reason),
                "HP_DELETION_RESTRICTED",
                "DELETE_HEALTH_PROVIDER"
        );
        this.id = id;
        this.reason = reason;
    }

    public Long getId() {
        return id;
    }

    public String getReason() {
        return reason;
    }
}
