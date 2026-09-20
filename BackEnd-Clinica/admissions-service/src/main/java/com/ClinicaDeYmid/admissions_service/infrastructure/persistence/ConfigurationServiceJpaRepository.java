package com.ClinicaDeYmid.admissions_service.infrastructure.persistence;

import com.ClinicaDeYmid.admissions_service.domain.ConfigurationService;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface ConfigurationServiceJpaRepository extends JpaRepository<ConfigurationService, Long> {

    @Query("select c from ConfigurationService c join fetch c.serviceType join fetch c.location where c.uuid = :uuid")
    Optional<ConfigurationService> findByUuid(@Param("uuid") UUID uuid);

    @Query("select c from ConfigurationService c join fetch c.serviceType s join fetch c.location l "
            + "where s.uuid = :serviceTypeUuid and l.uuid = :locationUuid")
    Optional<ConfigurationService> findByServiceTypeAndLocation(@Param("serviceTypeUuid") UUID serviceTypeUuid,
                                                                @Param("locationUuid") UUID locationUuid);

    @Query("select c from ConfigurationService c join fetch c.serviceType s join fetch c.location l "
            + "order by s.name, l.name")
    List<ConfigurationService> findAllOrdered();
}
