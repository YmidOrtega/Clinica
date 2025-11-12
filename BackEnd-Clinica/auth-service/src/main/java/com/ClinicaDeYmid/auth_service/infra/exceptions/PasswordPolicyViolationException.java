package com.ClinicaDeYmid.auth_service.infra.exceptions;

public class PasswordPolicyViolationException extends RuntimeException {
    public PasswordPolicyViolationException(String message) {
        super(message);
    }
}