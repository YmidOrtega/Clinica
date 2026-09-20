package com.ClinicaDeYmid.practitioners_service.repository;

import com.ClinicaDeYmid.practitioners_service.repository.entity.FeeAgreement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FeeAgreementRepository extends JpaRepository<FeeAgreement, Long> {

    @Query("""
            select distinct a from FeeAgreement a
            left join fetch a.lines
            where a.practitioner.uuid = :practitionerUuid
            order by a.validFrom desc
            """)
    List<FeeAgreement> ofPractitioner(@Param("practitionerUuid") UUID practitionerUuid);

    @Query("""
            select distinct a from FeeAgreement a
            left join fetch a.lines
            where a.practitioner.uuid = :practitionerUuid
              and a.validFrom <= :on
              and (a.revokedOn is null or a.revokedOn > :on)
            """)
    Optional<FeeAgreement> inForceOn(@Param("practitionerUuid") UUID practitionerUuid, @Param("on") LocalDate on);

    @Query("""
            select a from FeeAgreement a
            where a.practitioner.uuid = :practitionerUuid and a.revokedOn is null
            order by a.validFrom desc
            """)
    List<FeeAgreement> standing(@Param("practitionerUuid") UUID practitionerUuid);
}
