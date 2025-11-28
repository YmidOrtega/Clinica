package com.ClinicaDeYmid.clients_service.infra.exception;

import lombok.Getter;

/**
 * Excepción cuando se intenta activar un proveedor ya activo.
 */
@Getter
public class HealthProviderAlreadyActiveException extends HealthProviderException {

    private final String nit;
    private final String socialReason;

    /**
     * Constructor con NIT
     */
    public HealthProviderAlreadyActiveException(String nit, String socialReason) {
        super(
                String.format("El proveedor de salud '%s' (NIT: %s) ya se encuentra activo", socialReason, nit),
                "HP_ALREADY_ACTIVE",
                "ACTIVATE_HEALTH_PROVIDER"
        );
        this.nit = nit;
        this.socialReason = socialReason;
    }

    /**
     * Constructor con ID (LEGACY - mantener por compatibilidad)
     * @deprecated Usar constructor con NIT
     */
    @Deprecated
    public HealthProviderAlreadyActiveException(Long id, String socialReason) {
        super(
                String.format("El proveedor de salud '%s' (ID: %d) ya se encuentra activo", socialReason, id),
                "HP_ALREADY_ACTIVE",
                "ACTIVATE_HEALTH_PROVIDER"
        );
        this.nit = null;
        this.socialReason = socialReason;
    }

}