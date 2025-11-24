package com.ClinicaDeYmid.clients_service.infra.exception;

public class ContractAlreadyActiveException extends RuntimeException {
    public ContractAlreadyActiveException(Long contractId) {
        super("Contract with ID " + contractId + " is already active");
    }
}
