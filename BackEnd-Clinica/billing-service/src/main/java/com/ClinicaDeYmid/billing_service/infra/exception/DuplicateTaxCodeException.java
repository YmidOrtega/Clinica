package com.ClinicaDeYmid.billing_service.infra.exception;

public class DuplicateTaxCodeException extends RuntimeException {
    public DuplicateTaxCodeException(String code) {
        super("Ya existe un impuesto con el código '" + code + "'.");
    }
}
