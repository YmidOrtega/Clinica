package com.ClinicaDeYmid.admissions_service.infrastructure.persistence;

import com.ClinicaDeYmid.admissions_service.domain.Authorization;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface AuthorizationJpaRepository extends JpaRepository<Authorization, Long> {

    @Query("select a from Authorization a join fetch a.admission where a.uuid = :uuid")
    Optional<Authorization> findByUuid(@Param("uuid") UUID uuid);

    @Query("select a from Authorization a join fetch a.admission m where m.uuid = :admissionUuid and a.number = :number")
    Optional<Authorization> findByAdmissionAndNumber(@Param("admissionUuid") UUID admissionUuid,
                                                     @Param("number") String number);

    @Query("select a from Authorization a join fetch a.admission m where m.uuid = :admissionUuid order by a.createdAt")
    List<Authorization> findByAdmission(@Param("admissionUuid") UUID admissionUuid);
}
