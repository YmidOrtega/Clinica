package com.ClinicaDeYmid.billing_service.infrastructure.messaging;

class MalformedClinicalEventException extends RuntimeException {

    MalformedClinicalEventException(String message, Throwable cause) {
        super(message, cause);
    }
}
