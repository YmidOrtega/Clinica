package com.ClinicaDeYmid.admissions_service.domain.patient;

import java.util.Optional;
import java.util.UUID;

public interface PatientReferences {

    Optional<PatientReference> find(UUID uuid);

    Optional<PatientReference> findByDocument(String documentType, String documentNumber);

    boolean saveIfNewer(PatientReference reference);
}
