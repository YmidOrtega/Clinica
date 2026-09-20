package com.ClinicaDeYmid.admissions_service.application.patient;

import com.ClinicaDeYmid.admissions_service.domain.patient.PatientReference;

public sealed interface PatientLookup {

    record Found(PatientReference reference) implements PatientLookup {
    }

    record NotFound() implements PatientLookup {
    }

    record Unavailable() implements PatientLookup {
    }
}
