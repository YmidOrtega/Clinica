package com.ClinicaDeYmid.admissions_service.infrastructure.persistence;

import com.ClinicaDeYmid.admissions_service.domain.Admission;
import com.ClinicaDeYmid.admissions_service.domain.Coverage;
import com.ClinicaDeYmid.admissions_service.domain.DeathNotice;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface AdmissionJpaRepository extends JpaRepository<Admission, Long> {

    @Query("select distinct a from Admission a left join fetch a.phases p left join fetch p.configurationService c "
            + "left join fetch c.serviceType left join fetch c.location where a.uuid = :uuid")
    Optional<Admission> findByUuid(@Param("uuid") UUID uuid);

    @Query("select distinct a from Admission a left join fetch a.phases p left join fetch p.configurationService c "
            + "left join fetch c.serviceType left join fetch c.location "
            + "where a.patientUuid = :patientUuid and a.statusCode in "
            + "(com.ClinicaDeYmid.admissions_service.domain.AdmissionStatus.Code.REGISTERED, "
            + "com.ClinicaDeYmid.admissions_service.domain.AdmissionStatus.Code.ACTIVE)")
    List<Admission> findOpenByPatient(@Param("patientUuid") UUID patientUuid);

    @Query("select distinct a from Admission a left join fetch a.phases p left join fetch p.configurationService c "
            + "left join fetch c.serviceType left join fetch c.location where a.uuid in :uuids")
    List<Admission> findAllByUuidIn(@Param("uuids") Collection<UUID> uuids);

    @Query("select a from Admission a where a.coverage.status is not null and a.coverage.status <> :covered "
            + "order by a.createdAt desc")
    Page<Admission> findByCoverageOtherThan(@Param("covered") Coverage.Code covered, Pageable pageable);

    @Query("select a from Admission a where a.deathNotice.status = :pending order by a.createdAt desc")
    Page<Admission> findByDeathNoticeStatus(@Param("pending") DeathNotice.Status pending, Pageable pageable);

    @Query("select distinct a from Admission a left join fetch a.phases p left join fetch p.configurationService c "
            + "left join fetch c.serviceType left join fetch c.location "
            + "where exists (select 1 from AdmissionPhase q where q.admission = a and q.endedAt is null "
            + "and q.configurationService.uuid = :service) "
            + "and a.statusCode in (com.ClinicaDeYmid.admissions_service.domain.AdmissionStatus.Code.REGISTERED, "
            + "com.ClinicaDeYmid.admissions_service.domain.AdmissionStatus.Code.ACTIVE)")
    List<Admission> findQueueOf(@Param("service") UUID configurationServiceUuid);

    @Query("select distinct a from Admission a left join fetch a.phases p left join fetch p.configurationService c "
            + "left join fetch c.serviceType left join fetch c.location where a.bedUuid in :beds")
    List<Admission> findByBedUuidIn(@Param("beds") Collection<UUID> bedUuids);
}
