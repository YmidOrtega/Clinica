package com.ClinicaDeYmid.clinical_history_service.domain.encounter;

import com.ClinicaDeYmid.clinical_history_service.domain.clinician.Clinician;

import java.time.Instant;
import java.util.UUID;

public record EncounterClosure(UUID encounterId, Instant closedAt, Clinician closedBy) {
}
