package com.ClinicaDeYmid.clients_service.infra.exception;

import lombok.Getter;

/**
 * Excepción cuando se intenta desactivar un proveedor ya inactivo.
 */
@Getter
public class HealthProviderAlreadyInactiveException extends HealthProviderException {

    private final String nit;
    private final String socialReason;

    /**
     * Constructor con NIT
     */
    public HealthProviderAlreadyInactiveException(String nit, String socialReason) {
        super(
                String.format("El proveedor de salud '%s' (NIT: %s) ya se encuentra inactivo", socialReason, nit),
                "HP_ALREADY_INACTIVE",
                "DEACTIVATE_HEALTH_PROVIDER"
        );
        this.nit = nit;
        this.socialReason = socialReason;
    }

    /**
     * Constructor con ID (LEGACY - mantener por compatibilidad)
     * @deprecated Usar constructor con NIT
     */
    @Deprecated
    public HealthProviderAlreadyInactiveException(Long id, String socialReason) {
        super(
                String.format("El proveedor de salud '%s' (ID: %d) ya se encuentra inactivo", socialReason, id),
                "HP_ALREADY_INACTIVE",
                "DEACTIVATE_HEALTH_PROVIDER"
        );
        this.nit = null;
        this.socialReason = socialReason;
    }
}
