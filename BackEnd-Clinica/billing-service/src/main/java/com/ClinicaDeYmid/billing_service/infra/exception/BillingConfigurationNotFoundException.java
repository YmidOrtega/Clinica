package com.ClinicaDeYmid.billing_service.infra.exception;

public class BillingConfigurationNotFoundException extends RuntimeException {
    public BillingConfigurationNotFoundException() {
        super("No existe una configuración de facturación activa.");
    }
    public BillingConfigurationNotFoundException(Long id) {
        super("Configuración de facturación con ID " + id + " no encontrada.");
    }
}
