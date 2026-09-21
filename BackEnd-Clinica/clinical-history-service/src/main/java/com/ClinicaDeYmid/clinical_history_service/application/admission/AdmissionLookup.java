package com.ClinicaDeYmid.clinical_history_service.application.admission;

public sealed interface AdmissionLookup {

    record Found() implements AdmissionLookup {
    }

    record NotFound() implements AdmissionLookup {
    }

    record Unavailable() implements AdmissionLookup {
    }
}
