package com.ClinicaDeYmid.contracting_service.infrastructure.persistence;

import com.ClinicaDeYmid.contracting_service.domain.SurgicalRuleSet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

interface SurgicalRuleSetJpaRepository extends JpaRepository<SurgicalRuleSet, Long> {

    @Query("SELECT DISTINCT r FROM SurgicalRuleSet r JOIN FETCH r.manualVersion v LEFT JOIN FETCH r.components "
            + "WHERE v.uuid = :version")
    Optional<SurgicalRuleSet> findByVersion(@Param("version") UUID version);
}
