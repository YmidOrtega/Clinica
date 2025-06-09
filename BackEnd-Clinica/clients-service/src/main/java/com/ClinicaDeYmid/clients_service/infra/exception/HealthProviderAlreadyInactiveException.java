package com.ClinicaDeYmid.clients_service.infra.exception;

/**
 * Excepción cuando se intenta desactivar un proveedor ya inactivo
 */
public class HealthProviderAlreadyInactiveException extends HealthProviderException {

    private final Long id;
    private final String socialReason;

    public HealthProviderAlreadyInactiveException(Long id, String socialReason) {
        super(
                String.format("El proveedor de salud '%s' (ID: %d) ya se encuentra inactivo", socialReason, id),
                "HP_ALREADY_INACTIVE",
                "DEACTIVATE_HEALTH_PROVIDER"
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
