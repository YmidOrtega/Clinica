package com.ClinicaDeYmid.commons.openbao.transit;

public class OpenBaoUnavailableException extends RuntimeException {

    public OpenBaoUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
