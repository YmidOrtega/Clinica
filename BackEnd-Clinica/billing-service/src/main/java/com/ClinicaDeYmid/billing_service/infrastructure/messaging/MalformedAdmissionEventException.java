package com.ClinicaDeYmid.billing_service.infrastructure.messaging;

class MalformedAdmissionEventException extends RuntimeException {

    MalformedAdmissionEventException(String message, Throwable cause) {
        super(message, cause);
    }
}
