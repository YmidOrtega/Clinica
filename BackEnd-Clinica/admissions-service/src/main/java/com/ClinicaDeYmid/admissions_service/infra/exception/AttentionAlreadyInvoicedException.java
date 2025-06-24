package com.ClinicaDeYmid.admissions_service.infra.exception;

public class AttentionAlreadyInvoicedException extends RuntimeException {
    private final Long attentionId;
    private final String invoiceNumber;

    public AttentionAlreadyInvoicedException(Long attentionId, String invoiceNumber) {
        super(String.format("No se puede modificar la atención con ID: %d porque ya está facturada (Factura #%s)",
                attentionId, invoiceNumber != null ? invoiceNumber : "N/A"));
        this.attentionId = attentionId;
        this.invoiceNumber = invoiceNumber;
    }

    public Long getAttentionId() {
        return attentionId;
    }

    public String getInvoiceNumber() {
        return invoiceNumber;
    }
}
