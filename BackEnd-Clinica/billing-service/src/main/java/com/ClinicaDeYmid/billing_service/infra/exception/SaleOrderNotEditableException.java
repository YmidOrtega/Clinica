package com.ClinicaDeYmid.billing_service.infra.exception;

import com.ClinicaDeYmid.billing_service.module.sale.enums.SaleOrderStatus;

public class SaleOrderNotEditableException extends RuntimeException {
    public SaleOrderNotEditableException(Long id, SaleOrderStatus status) {
        super("La orden de venta " + id + " no puede modificarse en estado " + status.name()
                + ". Solo se permiten cambios en estado DRAFT.");
    }
}
