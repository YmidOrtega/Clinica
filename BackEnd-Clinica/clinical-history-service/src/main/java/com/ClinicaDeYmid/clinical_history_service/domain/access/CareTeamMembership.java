package com.ClinicaDeYmid.clinical_history_service.domain.access;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record CareTeamMembership(UUID encounterId, UUID patientUuid, UUID clinicianUuid, Instant joinedAt, Instant encounterClosedAt) {

    public CareTeamMembership {
        Objects.requireNonNull(encounterId, "encounterId");
        Objects.requireNonNull(patientUuid, "patientUuid");
        Objects.requireNonNull(clinicianUuid, "clinicianUuid");
        Objects.requireNonNull(joinedAt, "joinedAt");
    }

    public boolean isActiveAt(Instant now, Duration afterClosure) {
        return encounterClosedAt == null || !now.isAfter(encounterClosedAt.plus(afterClosure));
    }
}
