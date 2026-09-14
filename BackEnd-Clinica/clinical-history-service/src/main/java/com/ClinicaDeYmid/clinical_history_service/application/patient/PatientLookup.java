package com.ClinicaDeYmid.clinical_history_service.application.patient;

import com.ClinicaDeYmid.clinical_history_service.domain.patient.PatientReference;

public sealed interface PatientLookup {

    record Found(PatientReference patient) implements PatientLookup {
    }

    record NotFound() implements PatientLookup {
    }

    record Unavailable() implements PatientLookup {
    }
}
