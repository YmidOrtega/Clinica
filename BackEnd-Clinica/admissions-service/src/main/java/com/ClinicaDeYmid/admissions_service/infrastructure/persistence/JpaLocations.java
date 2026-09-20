package com.ClinicaDeYmid.admissions_service.infrastructure.persistence;

import com.ClinicaDeYmid.admissions_service.domain.Location;
import com.ClinicaDeYmid.admissions_service.domain.Locations;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
class JpaLocations implements Locations {

    private final LocationJpaRepository repository;

    JpaLocations(LocationJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public Location save(Location location) {
        return repository.saveAndFlush(location);
    }

    @Override
    public Optional<Location> findByUuid(UUID uuid) {
        return repository.findByUuid(uuid);
    }

    @Override
    public Optional<Location> findByName(String name) {
        return repository.findByName(name);
    }

    @Override
    public List<Location> findAll() {
        return repository.findAll(Sort.by("name"));
    }
}
