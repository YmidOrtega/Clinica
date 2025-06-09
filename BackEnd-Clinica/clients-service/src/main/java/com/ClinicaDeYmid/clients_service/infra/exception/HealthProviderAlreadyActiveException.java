package com.ClinicaDeYmid.clients_service.infra.exception;

/**
 * Excepción cuando se intenta activar un proveedor ya activo
 */
public class HealthProviderAlreadyActiveException extends HealthProviderException {

    private final Long id;
    private final String socialReason;

    public HealthProviderAlreadyActiveException(Long id, String socialReason) {
        super(
                String.format("El proveedor de salud '%s' (ID: %d) ya se encuentra activo", socialReason, id),
                "HP_ALREADY_ACTIVE",
                "ACTIVATE_HEALTH_PROVIDER"
        );
        this.id = id;
        this.socialReason = socialReason;
    }

    public Long getId() {
        return id;
    }

    public String getSocialReason() {
        return socialReason;
    }
}
