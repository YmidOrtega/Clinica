package com.ClinicaDeYmid.clients_service.infra.exception;

public class ContractNotFoundForStatusException extends RuntimeException {
    public ContractNotFoundForStatusException(Long contractId, String operation) {
        super(String.format("Contrato no encontrado con ID: %d para operación: %s", contractId, operation));
    }
}
