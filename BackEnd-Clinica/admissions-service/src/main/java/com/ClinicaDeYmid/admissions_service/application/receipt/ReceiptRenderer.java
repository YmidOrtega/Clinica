package com.ClinicaDeYmid.admissions_service.application.receipt;

public interface ReceiptRenderer {

    byte[] render(ReceiptContent content);
}
