package com.ClinicaDeYmid.admissions_service.domain;

public enum AdmissionKind {

    EMERGENCY(false),
    INPATIENT(true),
    OUTPATIENT(false);

    private final boolean bedRequired;

    AdmissionKind(boolean bedRequired) {
        this.bedRequired = bedRequired;
    }

    public boolean bedRequired() {
        return bedRequired;
    }

    public boolean coverageMayBlockAdmission() {
        return this != EMERGENCY;
    }
}
