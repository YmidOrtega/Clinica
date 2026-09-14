package com.ClinicaDeYmid.clinical_history_service.domain.update;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record VitalSignObservation(UUID id, UUID patientUuid, VitalSignKind kind, BigDecimal value, Instant measuredAt, NoteOrigin origin) {
}
