package com.ClinicaDeYmid.clinical_history_service.infrastructure.messaging;

class MalformedPatientEventException extends RuntimeException {

    MalformedPatientEventException(String message, Throwable cause) {
        super(message, cause);
    }
}
