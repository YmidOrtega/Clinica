package com.ClinicaDeYmid.admissions_service.infrastructure.messaging;

class MalformedPractitionerEventException extends RuntimeException {

    MalformedPractitionerEventException(String message, Throwable cause) {
        super(message, cause);
    }
}
