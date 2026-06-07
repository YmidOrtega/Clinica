package com.ClinicaDeYmid.billing_service.infra.exception;

public class ClientPriceOverrideNotFoundException extends RuntimeException {
    public ClientPriceOverrideNotFoundException(Long id) {
        super("Override de precio con ID " + id + " no encontrado.");
    }
}
