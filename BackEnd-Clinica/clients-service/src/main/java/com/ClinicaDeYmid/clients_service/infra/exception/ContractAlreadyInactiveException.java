package com.ClinicaDeYmid.clients_service.infra.exception;

public class ContractAlreadyInactiveException extends RuntimeException {
    public ContractAlreadyInactiveException(Long contractId, String contractNumber) {
        super(String.format("El contrato con ID: %d y número: %s ya está inactivo", contractId, contractNumber));
    }
}
