package com.ClinicaDeYmid.clinical_history_service.domain.encounter;

import com.ClinicaDeYmid.clinical_history_service.domain.clinician.Clinician;

import java.time.Instant;

public sealed interface EncounterStatus {

    record Open() implements EncounterStatus {
    }

    record Closed(Instant closedAt, Clinician closedBy) implements EncounterStatus {
    }
}
