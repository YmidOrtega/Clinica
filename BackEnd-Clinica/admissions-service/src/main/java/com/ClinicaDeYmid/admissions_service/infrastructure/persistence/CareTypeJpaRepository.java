package com.ClinicaDeYmid.admissions_service.infrastructure.persistence;

import com.ClinicaDeYmid.admissions_service.domain.CareType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface CareTypeJpaRepository extends JpaRepository<CareType, Long> {

    @Query("select c from CareType c join fetch c.serviceType where c.uuid = :uuid")
    Optional<CareType> findByUuid(@Param("uuid") UUID uuid);

    @Query("select c from CareType c join fetch c.serviceType s where c.name = :name and s.uuid = :serviceTypeUuid")
    Optional<CareType> findByNameAndServiceType(@Param("name") String name,
                                                @Param("serviceTypeUuid") UUID serviceTypeUuid);

    @Query("select c from CareType c join fetch c.serviceType s where s.uuid = :serviceTypeUuid order by c.name")
    List<CareType> findByServiceType(@Param("serviceTypeUuid") UUID serviceTypeUuid);
}
