package com.ClinicaDeYmid.clients_service.infra.exception;

/**
 * Excepción cuando no se encuentran proveedores en la consulta paginada
 */
public class NoHealthProvidersFoundException extends HealthProviderException {

    private final int page;
    private final int size;

    public NoHealthProvidersFoundException(int page, int size) {
        super(
                String.format("No se encontraron proveedores de salud activos en la página %d con tamaño %d", page, size),
                "HP_NO_RESULTS",
                "GET_ALL_HEALTH_PROVIDERS"
        );
        this.page = page;
        this.size = size;
    }

    public int getPage() {
        return page;
    }

    public int getSize() {
        return size;
    }
}
