package com.ClinicaDeYmid.clients_service.infra.exception;

public class ContractAlreadyActiveException extends RuntimeException {
    public ContractAlreadyActiveException(Long contractId, String contractNumber) {
        super(String.format("El contrato con ID: %d y número: %s ya está activo", contractId, contractNumber));
    }
}
