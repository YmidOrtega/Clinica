package com.ClinicaDeYmid.billing_service.infra.exception;

public class AttentionAlreadyHasDraftException extends RuntimeException {
    public AttentionAlreadyHasDraftException(Long attentionId) {
        super("La atención " + attentionId
                + " ya tiene una orden de venta en borrador. Confírmela o cancélela antes de crear una nueva.");
    }
}
