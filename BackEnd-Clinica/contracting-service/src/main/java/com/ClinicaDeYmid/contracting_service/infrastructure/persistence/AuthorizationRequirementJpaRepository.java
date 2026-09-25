package com.ClinicaDeYmid.contracting_service.infrastructure.persistence;

import com.ClinicaDeYmid.contracting_service.domain.AuthorizationRequirement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface AuthorizationRequirementJpaRepository extends JpaRepository<AuthorizationRequirement, Long> {

    @Query("SELECT r FROM AuthorizationRequirement r JOIN FETCH r.contract WHERE r.uuid = :uuid")
    Optional<AuthorizationRequirement> findByUuid(@Param("uuid") UUID uuid);

    @Query("SELECT r FROM AuthorizationRequirement r JOIN FETCH r.contract c WHERE c.uuid = :contract "
            + "ORDER BY r.cupsCode, r.validFrom DESC")
    List<AuthorizationRequirement> findByContract(@Param("contract") UUID contract);

    @Query("SELECT r FROM AuthorizationRequirement r JOIN FETCH r.contract c WHERE c.uuid = :contract "
            + "AND r.cupsCode = :code AND r.validFrom <= :date AND (r.revokedFrom IS NULL OR r.revokedFrom > :date)")
    List<AuthorizationRequirement> findApplying(@Param("contract") UUID contract, @Param("code") String code,
                                                @Param("date") LocalDate date);
}
