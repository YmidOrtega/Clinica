package com.ClinicaDeYmid.admissions_service.domain;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

import java.time.Instant;

@Entity
@DiscriminatorValue("OUTPATIENT")
public final class OutpatientPhase extends AdmissionPhase {

    protected OutpatientPhase() {
    }

    OutpatientPhase(Admission admission, ConfigurationService configurationService, Instant startedAt, String openingReason) {
        super(admission, configurationService, startedAt, openingReason);
    }

    @Override
    public boolean bedRequired() {
        return false;
    }

    @Override
    public boolean coverageMayBlockAdmission() {
        return true;
    }
}
