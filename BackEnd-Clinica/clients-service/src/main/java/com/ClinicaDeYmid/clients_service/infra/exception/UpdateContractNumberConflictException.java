package com.ClinicaDeYmid.clients_service.infra.exception;

public class UpdateContractNumberConflictException extends RuntimeException {
    public UpdateContractNumberConflictException(String contractNumber, String conflictingId) {
        super(String.format("El número de contrato %s ya existe para el contrato con ID: %s", contractNumber, conflictingId));
    }
}
