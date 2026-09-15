package com.ClinicaDeYmid.api_gateway.infrastructure.oauth;

public class AuthUnavailableException extends RuntimeException {

    public AuthUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
