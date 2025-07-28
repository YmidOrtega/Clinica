package com.ClinicaDeYmid.clients_service.infra.exception;

public class ContractDeletionRestrictedException extends RuntimeException {
    public ContractDeletionRestrictedException(Long contractId, String reason) {
        super(String.format("No se puede eliminar el contrato con ID: %d. Razón: %s", contractId, reason));
    }
}
