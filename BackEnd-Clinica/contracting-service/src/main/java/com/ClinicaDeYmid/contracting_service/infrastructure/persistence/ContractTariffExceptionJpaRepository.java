package com.ClinicaDeYmid.contracting_service.infrastructure.persistence;

import com.ClinicaDeYmid.contracting_service.domain.ContractTariffException;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface ContractTariffExceptionJpaRepository extends JpaRepository<ContractTariffException, Long> {

    @Query("SELECT e FROM ContractTariffException e JOIN FETCH e.contract WHERE e.uuid = :uuid")
    Optional<ContractTariffException> findByUuid(@Param("uuid") UUID uuid);

    @Query("SELECT e FROM ContractTariffException e JOIN FETCH e.contract c WHERE c.uuid = :contract "
            + "ORDER BY e.cupsCode, e.validFrom DESC")
    List<ContractTariffException> findByContract(@Param("contract") UUID contract);

    @Query("SELECT e FROM ContractTariffException e JOIN FETCH e.contract c WHERE c.uuid = :contract "
            + "AND e.cupsCode = :code AND e.validFrom <= :date AND (e.revokedFrom IS NULL OR e.revokedFrom > :date)")
    List<ContractTariffException> findApplying(@Param("contract") UUID contract, @Param("code") String code,
                                               @Param("date") LocalDate date);
}
