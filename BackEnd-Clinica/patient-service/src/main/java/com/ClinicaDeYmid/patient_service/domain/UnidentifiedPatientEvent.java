package com.ClinicaDeYmid.patient_service.domain;

import java.time.LocalDate;
import java.util.UUID;

public sealed interface UnidentifiedPatientEvent {

    record Registered() implements UnidentifiedPatientEvent {
    }

    record Identified(UUID patientUuid) implements UnidentifiedPatientEvent {
    }

    record IdentificationReverted(UUID previousPatientUuid) implements UnidentifiedPatientEvent {
    }

    record Died(LocalDate dateOfDeath) implements UnidentifiedPatientEvent {
    }
}
