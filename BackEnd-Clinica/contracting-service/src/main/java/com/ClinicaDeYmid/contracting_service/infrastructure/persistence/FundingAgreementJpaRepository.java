package com.ClinicaDeYmid.contracting_service.infrastructure.persistence;

import com.ClinicaDeYmid.contracting_service.domain.FundingAgreement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface FundingAgreementJpaRepository extends JpaRepository<FundingAgreement, Long> {

    @Query("SELECT a FROM FundingAgreement a JOIN FETCH a.contract WHERE a.uuid = :uuid")
    Optional<FundingAgreement> findByUuid(@Param("uuid") UUID uuid);

    @Query("SELECT a FROM FundingAgreement a JOIN FETCH a.contract c WHERE c.uuid = :contract ORDER BY a.validFrom DESC")
    List<FundingAgreement> findByContract(@Param("contract") UUID contract);

    @Query("SELECT a FROM FundingAgreement a JOIN FETCH a.contract c WHERE c.uuid = :contract "
            + "AND a.validFrom <= :date AND (a.revokedFrom IS NULL OR a.revokedFrom > :date)")
    List<FundingAgreement> findApplying(@Param("contract") UUID contract, @Param("date") LocalDate date);
}
