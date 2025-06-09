package com.ClinicaDeYmid.clients_service.infra.exception;

/**
 * Excepción cuando ya existe un proveedor con el mismo NIT
 */
public class DuplicateHealthProviderNitException extends HealthProviderException {

    private final String nit;

    public DuplicateHealthProviderNitException(String nit) {
        super(
                String.format("Ya existe un proveedor de salud registrado con el NIT: %s", nit),
                "HP_DUPLICATE_NIT",
                "CREATE_HEALTH_PROVIDER"
        );
        this.nit = nit;
    }

    public String getNit() {
        return nit;
    }
}