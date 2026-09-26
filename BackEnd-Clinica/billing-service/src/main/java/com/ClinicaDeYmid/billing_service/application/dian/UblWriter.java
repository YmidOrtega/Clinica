package com.ClinicaDeYmid.billing_service.application.dian;

public interface UblWriter {

    String invoice(ElectronicInvoice invoice);

    String creditNote(ElectronicCreditNote note);

    String attachedDocument(ElectronicAttachment attachment);
}
