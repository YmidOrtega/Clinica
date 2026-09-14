package com.ClinicaDeYmid.clinical_history_service.infrastructure.encryption;

public class EncryptedContentUnreadableException extends RuntimeException {

    public EncryptedContentUnreadableException(String message) {
        super(message);
    }

    EncryptedContentUnreadableException(String message, Throwable cause) {
        super(message, cause);
    }
}
