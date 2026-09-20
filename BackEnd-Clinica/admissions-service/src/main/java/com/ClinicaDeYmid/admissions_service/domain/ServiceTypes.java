package com.ClinicaDeYmid.admissions_service.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ServiceTypes {

    ServiceType save(ServiceType serviceType);

    Optional<ServiceType> findByUuid(UUID uuid);

    Optional<ServiceType> findByName(String name);

    List<ServiceType> findAll();
}
