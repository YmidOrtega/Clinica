package com.ClinicaDeYmid.admissions_service.infrastructure.persistence;

import com.ClinicaDeYmid.admissions_service.domain.Bed;
import com.ClinicaDeYmid.admissions_service.domain.BedStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface BedJpaRepository extends JpaRepository<Bed, Long> {

    @Query("select b from Bed b join fetch b.room r join fetch r.location where b.uuid = :uuid")
    Optional<Bed> findByUuid(@Param("uuid") UUID uuid);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from Bed b where b.uuid = :uuid")
    Optional<Bed> lockByUuid(@Param("uuid") UUID uuid);

    @Query("select b from Bed b join fetch b.room r join fetch r.location "
            + "where b.label = :label and r.uuid = :roomUuid")
    Optional<Bed> findByLabelAndRoom(@Param("label") String label, @Param("roomUuid") UUID roomUuid);

    @Query("select b from Bed b join fetch b.room r join fetch r.location "
            + "where r.uuid = :roomUuid order by b.label")
    List<Bed> findByRoom(@Param("roomUuid") UUID roomUuid);

    @Query("select b from Bed b join fetch b.room r join fetch r.location l "
            + "where l.uuid = :locationUuid and b.statusCode = :status order by r.name, b.label")
    List<Bed> findByLocationAndStatus(@Param("locationUuid") UUID locationUuid,
                                      @Param("status") BedStatus.Code status);
}
