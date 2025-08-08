package com.ClinicaDeYmid.clients_service.module.repository;

import com.ClinicaDeYmid.clients_service.module.entity.HealthProvider;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface HealthProviderRepository extends JpaRepository <HealthProvider, Long> {

    Optional<HealthProvider> findByNit_Value(String nit);

    Page<HealthProvider> findAllByActiveTrue (Pageable pageable);

}


