package com.ClinicaDeYmid.contracting_service.infrastructure.persistence;

import com.ClinicaDeYmid.contracting_service.domain.ContractPackage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface ContractPackageJpaRepository extends JpaRepository<ContractPackage, Long> {

    @Query("SELECT p FROM ContractPackage p JOIN FETCH p.contract WHERE p.uuid = :uuid")
    Optional<ContractPackage> findByUuid(@Param("uuid") UUID uuid);

    @Query("SELECT p FROM ContractPackage p JOIN FETCH p.contract c WHERE c.uuid = :contract ORDER BY p.code, p.validFrom DESC")
    List<ContractPackage> findByContract(@Param("contract") UUID contract);

    @Query("SELECT p FROM ContractPackage p JOIN FETCH p.contract c WHERE c.uuid = :contract "
            + "AND p.validFrom <= :date AND (p.revokedFrom IS NULL OR p.revokedFrom > :date) ORDER BY p.code")
    List<ContractPackage> findApplying(@Param("contract") UUID contract, @Param("date") LocalDate date);
}
