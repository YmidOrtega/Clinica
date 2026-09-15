package com.ClinicaDeYmid.commons.openbao.totp;

public class TotpRejectedException extends RuntimeException {

    public TotpRejectedException(String message, Throwable cause) {
        super(message, cause);
    }
}
