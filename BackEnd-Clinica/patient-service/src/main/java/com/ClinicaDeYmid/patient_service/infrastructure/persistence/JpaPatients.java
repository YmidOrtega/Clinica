package com.ClinicaDeYmid.patient_service.infrastructure.persistence;

import com.ClinicaDeYmid.patient_service.domain.IdentityDocument;
import com.ClinicaDeYmid.patient_service.domain.Patient;
import com.ClinicaDeYmid.patient_service.domain.Patients;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
class JpaPatients implements Patients {

    private final PatientJpaRepository repository;

    JpaPatients(PatientJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public Patient save(Patient patient) {
        return repository.saveAndFlush(patient);
    }

    @Override
    public Optional<Patient> findByUuid(UUID uuid) {
        return repository.findByUuid(uuid);
    }

    @Override
    public Optional<Patient> findByDocument(IdentityDocument document) {
        return repository.findByDocument(document.type(), document.number());
    }

    @Override
    public boolean existsByDocument(IdentityDocument document) {
        return repository.existsByDocument(document.type(), document.number());
    }

    @Override
    public Page<Patient> searchByName(NameQuery query, Pageable pageable) {
        PageRequest page = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
        return query.firstNamesPrefix() == null
                ? repository.searchByLastNames(query.lastNamesPrefix() + "%", page)
                : repository.searchByFullName(query.lastNamesPrefix() + "%", query.firstNamesPrefix() + "%", page);
    }
}
