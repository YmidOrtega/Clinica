package com.ClinicaDeYmid.patient_service.domain;

import java.time.LocalDate;

public sealed interface PatientEvent {

    record Registered() implements PatientEvent {
    }

    record DocumentChanged(IdentityDocument previousDocument) implements PatientEvent {
    }

    record DemographicsCorrected() implements PatientEvent {
    }

    record AffiliationUpdated() implements PatientEvent {
    }

    record Deactivated() implements PatientEvent {
    }

    record Reactivated() implements PatientEvent {
    }

    record Died(LocalDate dateOfDeath) implements PatientEvent {
    }
}
