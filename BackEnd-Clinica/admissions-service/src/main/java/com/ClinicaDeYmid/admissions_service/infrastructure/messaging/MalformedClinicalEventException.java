package com.ClinicaDeYmid.admissions_service.infrastructure.messaging;

class MalformedClinicalEventException extends RuntimeException {

    MalformedClinicalEventException(String message, Throwable cause) {
        super(message, cause);
    }
}
