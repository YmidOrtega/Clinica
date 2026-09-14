package com.ClinicaDeYmid.patient_service.infrastructure.persistence;

import com.ClinicaDeYmid.patient_service.domain.UnidentifiedPatient;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

interface UnidentifiedPatientJpaRepository extends JpaRepository<UnidentifiedPatient, Long> {

    Optional<UnidentifiedPatient> findByUuid(UUID uuid);
}
