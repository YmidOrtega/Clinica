package com.ClinicaDeYmid.patient_service.application;

import com.ClinicaDeYmid.patient_service.domain.Patient;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface PatientHistory {

    List<Revision> of(UUID patientUuid);

    enum ChangeType {
        CREATED,
        UPDATED
    }

    record Revision(long number, Instant revisedAt, String revisedBy, ChangeType changeType, Patient state) {
    }
}
