package com.ClinicaDeYmid.patient_service.domain;

public enum HealthRegime {
    CONTRIBUTORY,
    SUBSIDIZED,
    SPECIAL,
    UNINSURED;

    public boolean hasPayer() {
        return this != UNINSURED;
    }
}
