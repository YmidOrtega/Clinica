package com.ClinicaDeYmid.admissions_service.domain.patient;

import java.util.Optional;
import java.util.UUID;

public interface PatientReferences {

    Optional<PatientReference> find(UUID uuid);

    boolean saveIfNewer(PatientReference reference);
}
