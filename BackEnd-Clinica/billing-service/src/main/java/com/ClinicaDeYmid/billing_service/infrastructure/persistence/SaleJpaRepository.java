package com.ClinicaDeYmid.billing_service.infrastructure.persistence;

import com.ClinicaDeYmid.billing_service.domain.Sale;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface SaleJpaRepository extends JpaRepository<Sale, Long> {

    @EntityGraph(attributePaths = {"account", "lines"})
    @Query("select s from Sale s where s.uuid = :uuid")
    Optional<Sale> findByUuid(@Param("uuid") UUID uuid);

    @EntityGraph(attributePaths = {"account", "lines"})
    @Query("select distinct s from Sale s where s.account.uuid = :accountUuid order by s.sequence")
    List<Sale> findByAccount(@Param("accountUuid") UUID accountUuid);

    @Query("select count(s) from Sale s where s.account.uuid = :accountUuid")
    int countByAccount(@Param("accountUuid") UUID accountUuid);
}
