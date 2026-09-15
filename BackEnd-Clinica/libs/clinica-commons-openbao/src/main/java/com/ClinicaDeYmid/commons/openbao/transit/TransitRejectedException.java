package com.ClinicaDeYmid.commons.openbao.transit;

public class TransitRejectedException extends RuntimeException {

    public TransitRejectedException(String message, Throwable cause) {
        super(message, cause);
    }
}
