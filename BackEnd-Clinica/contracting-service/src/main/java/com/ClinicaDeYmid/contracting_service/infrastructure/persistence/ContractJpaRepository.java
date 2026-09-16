package com.ClinicaDeYmid.contracting_service.infrastructure.persistence;

import com.ClinicaDeYmid.contracting_service.domain.Contract;
import com.ClinicaDeYmid.contracting_service.domain.ContractStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface ContractJpaRepository extends JpaRepository<Contract, Long> {

    @Query("SELECT c FROM Contract c JOIN FETCH c.payer LEFT JOIN FETCH c.tariffVersion tv "
            + "LEFT JOIN FETCH tv.manual WHERE c.uuid = :uuid")
    Optional<Contract> findByUuid(@Param("uuid") UUID uuid);

    @Query("SELECT c FROM Contract c JOIN FETCH c.payer p LEFT JOIN FETCH c.tariffVersion tv "
            + "LEFT JOIN FETCH tv.manual WHERE p.uuid = :payer AND c.number = :number")
    Optional<Contract> findByNumber(@Param("payer") UUID payer, @Param("number") String number);

    @Query("SELECT c FROM Contract c JOIN FETCH c.payer p LEFT JOIN FETCH c.tariffVersion tv "
            + "LEFT JOIN FETCH tv.manual WHERE p.uuid = :payer ORDER BY c.validFrom DESC, c.id DESC")
    List<Contract> findByPayer(@Param("payer") UUID payer);

    @Query("SELECT c FROM Contract c JOIN FETCH c.payer p LEFT JOIN FETCH c.tariffVersion tv LEFT JOIN FETCH tv.manual "
            + "WHERE p.uuid = :payer AND c.statusCode = :active "
            + "AND c.validFrom <= :date AND (c.validTo IS NULL OR c.validTo >= :date) ORDER BY c.validFrom DESC")
    List<Contract> findInForceOn(@Param("payer") UUID payer, @Param("date") LocalDate date,
                                 @Param("active") ContractStatus.Code active);
}
