package com.ClinicaDeYmid.admissions_service.infrastructure.persistence;

import com.ClinicaDeYmid.admissions_service.domain.Admission;
import com.ClinicaDeYmid.admissions_service.domain.Coverage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface AdmissionJpaRepository extends JpaRepository<Admission, Long> {

    @Query("select distinct a from Admission a left join fetch a.phases p left join fetch p.configurationService c "
            + "left join fetch c.serviceType left join fetch c.location where a.uuid = :uuid")
    Optional<Admission> findByUuid(@Param("uuid") UUID uuid);

    @Query("select distinct a from Admission a left join fetch a.phases p left join fetch p.configurationService c "
            + "left join fetch c.serviceType left join fetch c.location where a.number = :number")
    Optional<Admission> findByNumber(@Param("number") String number);

    @Query("select distinct a from Admission a left join fetch a.phases p left join fetch p.configurationService c "
            + "left join fetch c.serviceType left join fetch c.location "
            + "where a.patientUuid = :patientUuid and a.statusCode in "
            + "(com.ClinicaDeYmid.admissions_service.domain.AdmissionStatus.Code.REGISTERED, "
            + "com.ClinicaDeYmid.admissions_service.domain.AdmissionStatus.Code.ACTIVE)")
    List<Admission> findOpenByPatient(@Param("patientUuid") UUID patientUuid);

    @Query("select distinct a from Admission a left join fetch a.phases p left join fetch p.configurationService c "
            + "left join fetch c.serviceType left join fetch c.location "
            + "where a.patientUuid = :patientUuid order by a.createdAt desc")
    List<Admission> findByPatient(@Param("patientUuid") UUID patientUuid);

    @Query("select distinct a from Admission a left join fetch a.phases p left join fetch p.configurationService c "
            + "left join fetch c.serviceType left join fetch c.location "
            + "where a.coverage.status is not null and a.coverage.status <> :covered "
            + "order by a.createdAt desc")
    List<Admission> findByCoverageOtherThan(@Param("covered") Coverage.Code covered);
}
