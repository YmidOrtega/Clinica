package com.ClinicaDeYmid.clients_service.infra.exception;

/**
 * Excepción cuando ya existe un contrato con el mismo número
 */
public class DuplicateContractNumberException extends HealthProviderException {

    private final String contractNumber;

    public DuplicateContractNumberException(String contractNumber) {
        super(
                String.format("Ya existe un contrato registrado con el número: %s", contractNumber),
                "HP_DUPLICATE_CONTRACT",
                "CREATE_HEALTH_PROVIDER"
        );
        this.contractNumber = contractNumber;
    }

    public String getContractNumber() {
        return contractNumber;
    }
}
