package com.ClinicaDeYmid.billing_service.infra.exception;

public class SaleOrderNotFoundException extends RuntimeException {
    public SaleOrderNotFoundException(Long id) {
        super("Orden de venta con ID " + id + " no encontrada.");
    }
}
