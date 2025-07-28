package com.ClinicaDeYmid.clients_service.infra.exception;

public class ContractNotFoundException extends RuntimeException {
    public ContractNotFoundException(Long contractId) {
        super(String.format("Contrato no encontrado con ID: %d", contractId));
    }
}
