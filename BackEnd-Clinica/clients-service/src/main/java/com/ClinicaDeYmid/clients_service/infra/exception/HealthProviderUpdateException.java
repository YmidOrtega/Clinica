package com.ClinicaDeYmid.clients_service.infra.exception;

/**
 * Excepción para errores de actualización de datos
 */
public class HealthProviderUpdateException extends HealthProviderException {

    private final String providerId;

    public HealthProviderUpdateException(String providerId, String reason, Throwable cause) {
        super(
                String.format("Error al actualizar el proveedor de salud con ID %s: %s", providerId, reason),
                "HP_UPDATE_ERROR",
                "UPDATE_HEALTH_PROVIDER",
                cause
        );
        this.providerId = providerId;
    }

    public String getProviderId() {
        return providerId;
    }
}
