package com.ClinicaDeYmid.admissions_service.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface Admissions {

    Admission save(Admission admission);

    Optional<Admission> findByUuid(UUID uuid);

    Optional<Admission> findByNumber(String number);

    Optional<Admission> findOpenByPatient(UUID patientUuid);

    List<Admission> findByPatient(UUID patientUuid);

    List<Admission> findWithPendingCoverage();

    String nextNumber(int year);
}
