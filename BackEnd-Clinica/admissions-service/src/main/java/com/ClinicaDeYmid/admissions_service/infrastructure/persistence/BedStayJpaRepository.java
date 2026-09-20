package com.ClinicaDeYmid.admissions_service.infrastructure.persistence;

import com.ClinicaDeYmid.admissions_service.domain.BedStay;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

interface BedStayJpaRepository extends JpaRepository<BedStay, Long> {

    @Query("select s from BedStay s join fetch s.bed b where b.uuid = :bedUuid and s.endedAt is null")
    Optional<BedStay> findOpenByBed(@Param("bedUuid") UUID bedUuid);

    @Query("select s from BedStay s join fetch s.bed where s.occupant = :occupant and s.endedAt is null")
    Optional<BedStay> findOpenByOccupant(@Param("occupant") UUID occupant);
}
