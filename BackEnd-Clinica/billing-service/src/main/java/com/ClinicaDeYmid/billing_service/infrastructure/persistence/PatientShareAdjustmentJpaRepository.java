package com.ClinicaDeYmid.billing_service.infrastructure.persistence;

import com.ClinicaDeYmid.billing_service.domain.PatientShareAdjustment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

interface PatientShareAdjustmentJpaRepository extends JpaRepository<PatientShareAdjustment, Long> {

    @Query("select a from PatientShareAdjustment a where a.account.uuid = :account order by a.createdAt, a.id")
    List<PatientShareAdjustment> findByAccount(@Param("account") UUID account);
}
