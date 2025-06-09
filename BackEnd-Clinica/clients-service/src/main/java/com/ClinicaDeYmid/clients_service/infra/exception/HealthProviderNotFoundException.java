package com.ClinicaDeYmid.clients_service.infra.exception;

/**
 * Excepción cuando el proveedor a actualizar no existe
 */
public class HealthProviderNotFoundException extends HealthProviderException {

    private final String identifier;
    private final String identifierType;

    public HealthProviderNotFoundException(String identifier, String identifierType) {
        super(
                String.format("Proveedor de salud no encontrado con %s: %s", identifierType, identifier),
                "HP_NOT_FOUND",
                "UPDATE_HEALTH_PROVIDER"
        );
        this.identifier = identifier;
        this.identifierType = identifierType;
    }

    // Constructor para búsqueda por NIT (más común)
    public HealthProviderNotFoundException(String nit) {
        this(nit, "NIT");
    }

    public String getIdentifier() {
        return identifier;
    }

    public String getIdentifierType() {
        return identifierType;
    }
}
