package com.ClinicaDeYmid.billing_service.infrastructure.persistence;

import com.ClinicaDeYmid.billing_service.domain.DianEnvironment;
import com.ClinicaDeYmid.billing_service.domain.NumberingResolution;
import com.ClinicaDeYmid.billing_service.domain.NumberingResolutions;
import com.ClinicaDeYmid.billing_service.domain.ResolutionStatus;
import org.springframework.stereotype.Repository;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
class JpaNumberingResolutions implements NumberingResolutions {

    private final NumberingResolutionJpaRepository repository;

    JpaNumberingResolutions(NumberingResolutionJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public NumberingResolution save(NumberingResolution resolution) {
        return repository.saveAndFlush(resolution);
    }

    @Override
    public Optional<NumberingResolution> findByUuid(UUID uuid) {
        return repository.findByUuid(uuid);
    }

    @Override
    public Optional<NumberingResolution> findActive() {
        return repository.findByStatus(ResolutionStatus.Code.ACTIVE);
    }

    @Override
    public Optional<NumberingResolution> lockActive() {
        return repository.lockByStatus(ResolutionStatus.Code.ACTIVE);
    }

    @Override
    public List<NumberingResolution> findAll() {
        return repository.findAllNewestFirst();
    }

    @Override
    public List<NumberingResolution> findOpenIn(DianEnvironment environment) {
        return repository.findByEnvironmentAndStatuses(environment,
                EnumSet.of(ResolutionStatus.Code.PENDING, ResolutionStatus.Code.ACTIVE));
    }

    @Override
    public List<NumberingResolution> findByPrefix(String prefix) {
        return repository.findByPrefix(prefix);
    }
}
