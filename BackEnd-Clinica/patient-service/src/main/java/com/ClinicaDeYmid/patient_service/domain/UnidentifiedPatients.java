package com.ClinicaDeYmid.patient_service.domain;

import java.util.Optional;
import java.util.UUID;

public interface UnidentifiedPatients {

    UnidentifiedPatient save(UnidentifiedPatient patient);

    Optional<UnidentifiedPatient> findByUuid(UUID uuid);

    String nextCode(int year);
}
