package com.ClinicaDeYmid.billing_service.infra.exception;

public class BillingConfigurationAlreadyActiveException extends RuntimeException {
    public BillingConfigurationAlreadyActiveException() {
        super("Ya existe una configuración de facturación activa. Desactívela antes de crear una nueva.");
    }
}
