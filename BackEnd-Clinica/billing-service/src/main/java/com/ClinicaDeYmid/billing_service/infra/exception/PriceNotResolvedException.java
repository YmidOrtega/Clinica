package com.ClinicaDeYmid.billing_service.infra.exception;

public class PriceNotResolvedException extends RuntimeException {
    public PriceNotResolvedException(Long contractId, Long portfolioId) {
        super("No se pudo resolver el precio para el contrato " + contractId
                + " y el portafolio " + portfolioId
                + ". Configure un override o un manual de cobro vigente.");
    }
}
