package com.ClinicaDeYmid.admissions_service.infrastructure.persistence;

import com.ClinicaDeYmid.admissions_service.domain.ServiceType;
import com.ClinicaDeYmid.admissions_service.domain.ServiceTypes;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
class JpaServiceTypes implements ServiceTypes {

    private final ServiceTypeJpaRepository repository;

    JpaServiceTypes(ServiceTypeJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public ServiceType save(ServiceType serviceType) {
        return repository.saveAndFlush(serviceType);
    }

    @Override
    public Optional<ServiceType> findByUuid(UUID uuid) {
        return repository.findByUuid(uuid);
    }

    @Override
    public Optional<ServiceType> findByName(String name) {
        return repository.findByName(name);
    }

    @Override
    public List<ServiceType> findAll() {
        return repository.findAll(Sort.by("name"));
    }
}
