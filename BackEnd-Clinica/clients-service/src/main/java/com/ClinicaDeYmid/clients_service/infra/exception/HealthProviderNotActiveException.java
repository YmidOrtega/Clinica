package com.ClinicaDeYmid.clients_service.infra.exception;
/**
 * Excepción mejorada para proveedor no activo
 */
public class HealthProviderNotActiveException extends HealthProviderException {

    private final String socialReason;
    private final String nit;

    public HealthProviderNotActiveException(String socialReason, String nit) {
        super(
                String.format("El proveedor de salud '%s' (NIT: %s) no está activo y no puede ser consultado", socialReason, nit),
                "HP_NOT_ACTIVE",
                "GET_HEALTH_PROVIDER"
        );
        this.socialReason = socialReason;
        this.nit = nit;
    }

    public String getSocialReason() {
        return socialReason;
    }

    public String getNit() {
        return nit;
    }
}