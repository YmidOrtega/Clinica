package com.ClinicaDeYmid.clients_service.infra.exception;

public class ContractAlreadyInactiveException extends RuntimeException {
    public ContractAlreadyInactiveException(Long contractId) {
        super("Contract with ID " + contractId + " is already inactive");
    }
}
