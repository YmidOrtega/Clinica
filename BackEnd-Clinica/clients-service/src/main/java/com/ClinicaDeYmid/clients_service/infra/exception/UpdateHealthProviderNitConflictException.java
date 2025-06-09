package com.ClinicaDeYmid.clients_service.infra.exception;

/**
 * Excepción cuando se intenta actualizar con un NIT que ya existe
 */
public class UpdateHealthProviderNitConflictException extends HealthProviderException {

    private final String newNit;
    private final String existingProviderId;

    public UpdateHealthProviderNitConflictException(String newNit, String existingProviderId) {
        super(
                String.format("No se puede actualizar: ya existe otro proveedor con el NIT %s (ID: %s)", newNit, existingProviderId),
                "HP_UPDATE_NIT_CONFLICT",
                "UPDATE_HEALTH_PROVIDER"
        );
        this.newNit = newNit;
        this.existingProviderId = existingProviderId;
    }

    public String getNewNit() {
        return newNit;
    }

    public String getExistingProviderId() {
        return existingProviderId;
    }
}
