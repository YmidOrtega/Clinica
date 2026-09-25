package com.ClinicaDeYmid.billing_service.application.context;

public sealed interface PatientLookup {

    record Found(PatientDetails patient) implements PatientLookup {
    }

    record NotFound() implements PatientLookup {
    }

    record Unavailable() implements PatientLookup {
    }
}
