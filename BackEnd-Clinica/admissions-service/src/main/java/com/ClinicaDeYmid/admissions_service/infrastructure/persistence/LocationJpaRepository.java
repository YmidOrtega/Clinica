package com.ClinicaDeYmid.admissions_service.infrastructure.persistence;

import com.ClinicaDeYmid.admissions_service.domain.Location;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

interface LocationJpaRepository extends JpaRepository<Location, Long> {

    Optional<Location> findByUuid(UUID uuid);

    Optional<Location> findByName(String name);
}
