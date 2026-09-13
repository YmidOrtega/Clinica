package com.ClinicaDeYmid.patient_service.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;
import java.util.UUID;

public interface Patients {

    Patient save(Patient patient);

    Optional<Patient> findByUuid(UUID uuid);

    Optional<Patient> findByDocument(IdentityDocument document);

    boolean existsByDocument(IdentityDocument document);

    Page<Patient> searchByName(NameQuery query, Pageable pageable);

    record NameQuery(String lastNamesPrefix, String firstNamesPrefix) {
    }
}
