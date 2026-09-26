package com.ClinicaDeYmid.billing_service.infrastructure.persistence;

import com.ClinicaDeYmid.billing_service.domain.DianVerdict;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

interface DianVerdictJpaRepository extends JpaRepository<DianVerdict, Long> {

    @Query("select v from DianVerdict v where v.document.uuid = :document order by v.receivedAt, v.id")
    List<DianVerdict> ofDocument(@Param("document") UUID document);
}
