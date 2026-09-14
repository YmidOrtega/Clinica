package com.ClinicaDeYmid.clinical_history_service.domain.update;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface PatientChart {

    List<ListItemHistory> listItemsOf(Collection<UUID> patientUuids);

    List<VitalSignObservation> vitalSignsOf(Collection<UUID> patientUuids, VitalSignKind kind, Instant from, Instant to);
}
