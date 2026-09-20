package com.ClinicaDeYmid.admissions_service.domain;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

import java.time.Instant;

@Entity
@DiscriminatorValue("EMERGENCY")
public final class EmergencyPhase extends AdmissionPhase {

    protected EmergencyPhase() {
    }

    EmergencyPhase(Admission admission, ConfigurationService configurationService, Instant startedAt, String openingReason) {
        super(admission, configurationService, startedAt, openingReason);
    }

    @Override
    public boolean bedRequired() {
        return false;
    }

    @Override
    public boolean coverageMayBlockAdmission() {
        return false;
    }
}
