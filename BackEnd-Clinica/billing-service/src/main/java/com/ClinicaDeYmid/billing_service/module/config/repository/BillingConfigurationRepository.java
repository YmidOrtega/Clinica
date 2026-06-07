package com.ClinicaDeYmid.billing_service.module.config.repository;

import com.ClinicaDeYmid.billing_service.module.config.entity.BillingConfiguration;
import com.ClinicaDeYmid.billing_service.module.config.enums.DianEnvironment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface BillingConfigurationRepository extends JpaRepository<BillingConfiguration, Long> {

    Optional<BillingConfiguration> findByActiveTrue();

    boolean existsByActiveTrue();

    boolean existsByClinicNit(String clinicNit);

    Optional<BillingConfiguration> findByDianEnvironment(DianEnvironment dianEnvironment);
}
