package com.ClinicaDeYmid.billing_service.module.config.repository;

import com.ClinicaDeYmid.billing_service.module.config.entity.TaxConfiguration;
import com.ClinicaDeYmid.billing_service.module.config.enums.TaxType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TaxConfigurationRepository extends JpaRepository<TaxConfiguration, Long> {

    Optional<TaxConfiguration> findByCode(String code);

    boolean existsByCode(String code);

    List<TaxConfiguration> findByActiveTrueOrderByTypeAscNameAsc();

    List<TaxConfiguration> findByTypeAndActiveTrue(TaxType type);

    @Query("""
            SELECT t FROM TaxConfiguration t
            WHERE t.active = true
            AND t.appliesToServices = true
            ORDER BY t.type ASC, t.name ASC
            """)
    List<TaxConfiguration> findActiveForServices();

    @Query("""
            SELECT t FROM TaxConfiguration t
            WHERE t.active = true
            AND t.appliesToMedications = true
            ORDER BY t.type ASC, t.name ASC
            """)
    List<TaxConfiguration> findActiveForMedications();
}
