package com.ClinicaDeYmid.auth_service.infra.exceptions;

public class TokenExpiredException extends Throwable {
    public TokenExpiredException(String message) {
        super(message);
    }
}
