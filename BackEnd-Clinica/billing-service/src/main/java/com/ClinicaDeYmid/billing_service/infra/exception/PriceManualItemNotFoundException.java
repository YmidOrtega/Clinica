package com.ClinicaDeYmid.billing_service.infra.exception;

public class PriceManualItemNotFoundException extends RuntimeException {
    public PriceManualItemNotFoundException(Long id) {
        super("Ítem de manual con ID " + id + " no encontrado.");
    }
}
