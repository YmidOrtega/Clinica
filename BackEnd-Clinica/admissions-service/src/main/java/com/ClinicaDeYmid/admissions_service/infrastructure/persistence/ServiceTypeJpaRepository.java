package com.ClinicaDeYmid.admissions_service.infrastructure.persistence;

import com.ClinicaDeYmid.admissions_service.domain.ServiceType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

interface ServiceTypeJpaRepository extends JpaRepository<ServiceType, Long> {

    Optional<ServiceType> findByUuid(UUID uuid);

    Optional<ServiceType> findByName(String name);
}
