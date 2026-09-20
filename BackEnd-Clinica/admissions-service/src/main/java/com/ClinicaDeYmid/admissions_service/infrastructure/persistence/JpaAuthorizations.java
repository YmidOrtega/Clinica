package com.ClinicaDeYmid.admissions_service.infrastructure.persistence;

import com.ClinicaDeYmid.admissions_service.domain.Authorization;
import com.ClinicaDeYmid.admissions_service.domain.Authorizations;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
class JpaAuthorizations implements Authorizations {

    private final AuthorizationJpaRepository repository;

    JpaAuthorizations(AuthorizationJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public Authorization save(Authorization authorization) {
        return repository.saveAndFlush(authorization);
    }

    @Override
    public Optional<Authorization> findByUuid(UUID uuid) {
        return repository.findByUuid(uuid);
    }

    @Override
    public Optional<Authorization> findByAdmissionAndNumber(UUID admissionUuid, String number) {
        return repository.findByAdmissionAndNumber(admissionUuid, number);
    }

    @Override
    public List<Authorization> findByAdmission(UUID admissionUuid) {
        return repository.findByAdmission(admissionUuid);
    }
}
