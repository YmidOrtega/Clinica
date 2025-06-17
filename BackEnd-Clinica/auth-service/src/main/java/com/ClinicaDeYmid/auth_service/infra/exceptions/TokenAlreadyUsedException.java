package com.ClinicaDeYmid.auth_service.infra.exceptions;

public class TokenAlreadyUsedException extends RuntimeException {
    public TokenAlreadyUsedException(String message) {
        super(message);
    }

    public TokenAlreadyUsedException(String message, Throwable cause) {
        super(message, cause);
    }
}
