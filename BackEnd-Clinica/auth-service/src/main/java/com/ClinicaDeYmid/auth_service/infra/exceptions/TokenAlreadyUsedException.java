package com.ClinicaDeYmid.auth_service.infra.exceptions;

public class TokenAlreadyUsedException extends Throwable {
    public TokenAlreadyUsedException(String message) {
        super(message);
    }
}
