package com.ClinicaDeYmid.clinical_history_service.domain.access;

import com.ClinicaDeYmid.clinical_history_service.domain.clinician.Clinician;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface CareTeams {

    boolean add(UUID encounterId, Clinician member, Clinician addedBy, Instant addedAt);

    List<CareTeamMembership> membershipsOf(UUID clinicianUuid, Collection<UUID> patientUuids);
}
