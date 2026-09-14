package com.ClinicaDeYmid.clinical_history_service.domain.encounter;

public enum EncounterType {
    OUTPATIENT,
    EMERGENCY,
    INPATIENT,
    TELEHEALTH;

    public boolean requiresDischargeSummary() {
        return this == EMERGENCY || this == INPATIENT;
    }
}
