package com.ClinicaDeYmid.contracting_service.application;

import java.util.UUID;

public sealed interface PatientLookup {

    record Found(UUID patientUuid) implements PatientLookup {
    }

    record NotFound() implements PatientLookup {
    }

    record Unavailable() implements PatientLookup {
    }
}
