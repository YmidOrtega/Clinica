package com.ClinicaDeYmid.admissions_service.infrastructure.persistence;

import com.ClinicaDeYmid.admissions_service.domain.CareType;
import com.ClinicaDeYmid.admissions_service.domain.CareTypes;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
class JpaCareTypes implements CareTypes {

    private final CareTypeJpaRepository repository;

    JpaCareTypes(CareTypeJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public CareType save(CareType careType) {
        return repository.saveAndFlush(careType);
    }

    @Override
    public Optional<CareType> findByUuid(UUID uuid) {
        return repository.findByUuid(uuid);
    }

    @Override
    public Optional<CareType> findByNameAndServiceType(String name, UUID serviceTypeUuid) {
        return repository.findByNameAndServiceType(name, serviceTypeUuid);
    }

    @Override
    public List<CareType> findByServiceType(UUID serviceTypeUuid) {
        return repository.findByServiceType(serviceTypeUuid);
    }
}
