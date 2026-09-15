package com.ClinicaDeYmid.auth_service.domain.secondfactor;

public enum AuthenticationMethod {

    PASSWORD("pwd"),
    TOTP("otp"),
    RECOVERY_CODE("rec"),
    MULTI_FACTOR("mfa");

    private final String amr;

    AuthenticationMethod(String amr) {
        this.amr = amr;
    }

    public String amr() {
        return amr;
    }
}
