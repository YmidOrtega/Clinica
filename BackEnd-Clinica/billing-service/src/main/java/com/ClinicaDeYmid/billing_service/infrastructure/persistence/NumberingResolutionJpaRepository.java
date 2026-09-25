package com.ClinicaDeYmid.billing_service.infrastructure.persistence;

import com.ClinicaDeYmid.billing_service.domain.DianEnvironment;
import com.ClinicaDeYmid.billing_service.domain.NumberingResolution;
import com.ClinicaDeYmid.billing_service.domain.ResolutionStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface NumberingResolutionJpaRepository extends JpaRepository<NumberingResolution, Long> {

    @Query("select r from NumberingResolution r where r.uuid = :uuid")
    Optional<NumberingResolution> findByUuid(@Param("uuid") UUID uuid);

    @Query("select r from NumberingResolution r where r.statusCode = :status")
    Optional<NumberingResolution> findByStatus(@Param("status") ResolutionStatus.Code status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from NumberingResolution r where r.statusCode = :status")
    Optional<NumberingResolution> lockByStatus(@Param("status") ResolutionStatus.Code status);

    @Query("select r from NumberingResolution r where r.environment = :environment and r.statusCode in :statuses")
    List<NumberingResolution> findByEnvironmentAndStatuses(@Param("environment") DianEnvironment environment,
                                                          @Param("statuses") Collection<ResolutionStatus.Code> statuses);

    @Query("select r from NumberingResolution r where r.prefix = :prefix")
    List<NumberingResolution> findByPrefix(@Param("prefix") String prefix);

    @Query("select r from NumberingResolution r order by r.validFrom desc, r.id desc")
    List<NumberingResolution> findAllNewestFirst();
}
