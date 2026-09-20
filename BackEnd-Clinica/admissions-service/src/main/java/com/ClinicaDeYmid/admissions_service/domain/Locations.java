package com.ClinicaDeYmid.admissions_service.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface Locations {

    Location save(Location location);

    Optional<Location> findByUuid(UUID uuid);

    Optional<Location> findByName(String name);

    List<Location> findAll();
}
