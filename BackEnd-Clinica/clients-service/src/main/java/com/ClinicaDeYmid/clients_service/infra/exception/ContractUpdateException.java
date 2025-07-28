package com.ClinicaDeYmid.clients_service.infra.exception;

public class ContractUpdateException extends RuntimeException {
    public ContractUpdateException(Long contractId, String message, Throwable cause) {
        super(String.format("Error al actualizar contrato con ID: %d. %s", contractId, message), cause);
    }
}
