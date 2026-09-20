package com.ClinicaDeYmid.admissions_service.infrastructure.persistence;

import com.ClinicaDeYmid.admissions_service.domain.ConfigurationService;
import com.ClinicaDeYmid.admissions_service.domain.ConfigurationServices;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
class JpaConfigurationServices implements ConfigurationServices {

    private final ConfigurationServiceJpaRepository repository;

    JpaConfigurationServices(ConfigurationServiceJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public ConfigurationService save(ConfigurationService configurationService) {
        return repository.saveAndFlush(configurationService);
    }

    @Override
    public Optional<ConfigurationService> findByUuid(UUID uuid) {
        return repository.findByUuid(uuid);
    }

    @Override
    public Optional<ConfigurationService> findByServiceTypeAndLocation(UUID serviceTypeUuid, UUID locationUuid) {
        return repository.findByServiceTypeAndLocation(serviceTypeUuid, locationUuid);
    }

    @Override
    public List<ConfigurationService> findAll() {
        return repository.findAllOrdered();
    }
}
