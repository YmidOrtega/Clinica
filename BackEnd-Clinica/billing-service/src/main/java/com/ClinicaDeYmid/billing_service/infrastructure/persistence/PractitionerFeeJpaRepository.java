package com.ClinicaDeYmid.billing_service.infrastructure.persistence;

import com.ClinicaDeYmid.billing_service.domain.PractitionerFee;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

interface PractitionerFeeJpaRepository extends JpaRepository<PractitionerFee, Long> {

    @Query("select f from PractitionerFee f join fetch f.sale s join fetch s.account where s.uuid = :sale "
            + "order by f.saleLineUuid, f.role")
    List<PractitionerFee> findBySale(@Param("sale") UUID sale);

    @Query("select f from PractitionerFee f join fetch f.sale s join fetch s.account "
            + "where f.practitionerUuid = :practitioner and (:status is null or f.status = :status) "
            + "order by f.performedOn desc, f.id desc")
    List<PractitionerFee> findByPractitioner(@Param("practitioner") UUID practitioner,
                                             @Param("status") PractitionerFee.Status status, Pageable page);
}
