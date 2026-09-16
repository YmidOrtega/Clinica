package com.ClinicaDeYmid.contracting_service.infrastructure.persistence;

import com.ClinicaDeYmid.contracting_service.domain.TariffManual;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface TariffManualJpaRepository extends JpaRepository<TariffManual, Long> {

    Optional<TariffManual> findByUuid(UUID uuid);

    Optional<TariffManual> findByCode(String code);

    List<TariffManual> findAllByOrderByCode();
}
