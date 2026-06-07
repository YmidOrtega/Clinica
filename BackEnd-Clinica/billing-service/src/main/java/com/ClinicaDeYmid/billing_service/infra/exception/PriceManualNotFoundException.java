package com.ClinicaDeYmid.billing_service.infra.exception;

public class PriceManualNotFoundException extends RuntimeException {
    public PriceManualNotFoundException(Long id) {
        super("Manual de cobro con ID " + id + " no encontrado.");
    }
    public PriceManualNotFoundException(String code) {
        super("Manual de cobro con código '" + code + "' no encontrado.");
    }
}
