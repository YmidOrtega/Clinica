package com.ClinicaDeYmid.billing_service.domain;

public enum AuthorizationCheck {
    NOT_REQUIRED,
    AUTHORIZED,
    EMERGENCY_EXEMPT,
    MISSING;

    public boolean blocks() {
        return this == MISSING;
    }
}
