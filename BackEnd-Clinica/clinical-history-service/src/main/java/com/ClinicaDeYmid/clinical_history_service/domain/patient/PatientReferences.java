package com.ClinicaDeYmid.clinical_history_service.domain.patient;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PatientReferences {

    Optional<PatientReference> find(UUID uuid);

    boolean saveIfNewer(PatientReference reference);

    List<UUID> subjectsOf(UUID patientUuid);
}
