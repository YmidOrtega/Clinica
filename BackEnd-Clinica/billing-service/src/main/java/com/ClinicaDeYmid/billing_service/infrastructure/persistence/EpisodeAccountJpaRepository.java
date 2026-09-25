package com.ClinicaDeYmid.billing_service.infrastructure.persistence;

import com.ClinicaDeYmid.billing_service.domain.AccountStatus;
import com.ClinicaDeYmid.billing_service.domain.EpisodeAccount;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface EpisodeAccountJpaRepository extends JpaRepository<EpisodeAccount, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from EpisodeAccount a where a.admissionUuid = :admissionUuid")
    Optional<EpisodeAccount> lockByAdmissionUuid(@Param("admissionUuid") UUID admissionUuid);

    @Query("select a from EpisodeAccount a where a.admissionNumber = :number")
    Optional<EpisodeAccount> findByAdmissionNumber(@Param("number") String number);

    @Query("select a from EpisodeAccount a where a.statusCode = :status order by a.statusChangedAt, a.openedAt")
    List<EpisodeAccount> findByStatus(@Param("status") AccountStatus.Code status, Pageable page);
}
