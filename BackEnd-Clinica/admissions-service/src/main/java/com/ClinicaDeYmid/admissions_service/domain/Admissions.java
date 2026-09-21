package com.ClinicaDeYmid.admissions_service.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface Admissions {

    Admission save(Admission admission);

    Optional<Admission> findByUuid(UUID uuid);

    Optional<Admission> findOpenByPatient(UUID patientUuid);

    Page<Admission> search(AdmissionSearch criteria, Pageable pageable);

    Page<Admission> findWithPendingCoverage(Pageable pageable);

    Page<Admission> findWithPendingDeathNotice(Pageable pageable);

    List<Admission> findQueueOf(UUID configurationServiceUuid);

    List<Admission> findByBeds(Collection<UUID> bedUuids);

    String nextNumber(int year);
}
