package com.ClinicaDeYmid.api_gateway.infrastructure.oauth;

public class SessionExpiredException extends RuntimeException {

    public SessionExpiredException(String message) {
        super(message);
    }
}
