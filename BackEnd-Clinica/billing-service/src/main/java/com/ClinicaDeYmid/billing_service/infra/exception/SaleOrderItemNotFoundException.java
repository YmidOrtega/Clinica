package com.ClinicaDeYmid.billing_service.infra.exception;

public class SaleOrderItemNotFoundException extends RuntimeException {
    public SaleOrderItemNotFoundException(Long id) {
        super("Ítem de venta con ID " + id + " no encontrado.");
    }
}
