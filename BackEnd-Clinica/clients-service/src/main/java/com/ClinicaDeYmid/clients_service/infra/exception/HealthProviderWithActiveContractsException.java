package com.ClinicaDeYmid.clients_service.infra.exception;

import lombok.Getter;

@Getter
public class HealthProviderWithActiveContractsException extends HealthProviderException {

    private final String nit;
    private final String socialReason;

    public HealthProviderWithActiveContractsException(String nit, String socialReason) {
        super(
                String.format("El proveedor de salud '%s' (NIT: %s) tiene contratos activos y no puede ser desactivado o eliminado",
                        socialReason, nit),
                "HP_HAS_ACTIVE_CONTRACTS",
                "DEACTIVATE_OR_DELETE_HEALTH_PROVIDER"
        );
        this.nit = nit;
        this.socialReason = socialReason;
    }

}