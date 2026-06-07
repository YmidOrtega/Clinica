package com.ClinicaDeYmid.billing_service.infra.exception;

public class DuplicatePriceManualCodeException extends RuntimeException {
    public DuplicatePriceManualCodeException(String code) {
        super("Ya existe un manual de cobro con el código '" + code + "'.");
    }
}
