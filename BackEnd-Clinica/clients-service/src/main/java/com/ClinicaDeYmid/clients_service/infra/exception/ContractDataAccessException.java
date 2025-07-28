package com.ClinicaDeYmid.clients_service.infra.exception;

public class ContractDataAccessException extends RuntimeException {
    public ContractDataAccessException(String operation, Throwable cause) {
        super(String.format("Error de acceso a datos al %s", operation), cause);
    }
}
