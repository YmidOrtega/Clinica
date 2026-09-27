package com.ClinicaDeYmid.ai_assistant_service.messaging;

class MalformedInvoiceEventException extends RuntimeException {

    MalformedInvoiceEventException(String message, Throwable cause) {
        super(message, cause);
    }
}
