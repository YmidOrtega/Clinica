package com.ClinicaDeYmid.billing_service.infra.exception;

public class TaxConfigurationNotFoundException extends RuntimeException {
    public TaxConfigurationNotFoundException(Long id) {
        super("Configuración de impuesto con ID " + id + " no encontrada.");
    }
    public TaxConfigurationNotFoundException(String code) {
        super("Configuración de impuesto con código '" + code + "' no encontrada.");
    }
}
