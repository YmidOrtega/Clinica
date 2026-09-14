package com.ClinicaDeYmid.clinical_history_service.infrastructure.transit;

public class TransitRejectedException extends RuntimeException {

    TransitRejectedException(String message, Throwable cause) {
        super(message, cause);
    }
}
