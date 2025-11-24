package com.ClinicaDeYmid.clients_service.infra.exception;

public class ContractValidationException extends RuntimeException {
    public ContractValidationException(String message) {
        super(message);
    }
}