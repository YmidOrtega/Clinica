package com.ClinicaDeYmid.admissions_service.domain;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

import java.time.Instant;

@Entity
@DiscriminatorValue("INPATIENT")
public final class InpatientPhase extends AdmissionPhase {

    protected InpatientPhase() {
    }

    InpatientPhase(Admission admission, ConfigurationService configurationService, Instant startedAt, String openingReason) {
        super(admission, configurationService, startedAt, openingReason);
    }

    @Override
    public boolean bedRequired() {
        return true;
    }

    @Override
    public boolean coverageMayBlockAdmission() {
        return true;
    }
}
