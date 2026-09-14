package com.ClinicaDeYmid.clinical_history_service.infrastructure.transit;

public class ClinicalKeysUnavailableException extends RuntimeException {

    ClinicalKeysUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
