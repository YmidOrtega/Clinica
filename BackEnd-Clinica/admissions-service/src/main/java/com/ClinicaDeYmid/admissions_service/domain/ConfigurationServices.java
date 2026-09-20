package com.ClinicaDeYmid.admissions_service.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ConfigurationServices {

    ConfigurationService save(ConfigurationService configurationService);

    Optional<ConfigurationService> findByUuid(UUID uuid);

    Optional<ConfigurationService> findByServiceTypeAndLocation(UUID serviceTypeUuid, UUID locationUuid);

    List<ConfigurationService> findAll();
}
