package com.ClinicaDeYmid.clinical_history_service.domain.access;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface EmergencyAccesses {

    void add(EmergencyAccess access);

    List<EmergencyAccess> activeFor(UUID clinicianUuid, Collection<UUID> patientUuids, Instant now);
}
