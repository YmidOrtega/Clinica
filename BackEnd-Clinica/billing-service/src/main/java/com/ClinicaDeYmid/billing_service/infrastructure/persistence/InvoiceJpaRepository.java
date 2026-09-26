package com.ClinicaDeYmid.billing_service.infrastructure.persistence;

import com.ClinicaDeYmid.billing_service.domain.Invoice;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface InvoiceJpaRepository extends JpaRepository<Invoice, Long> {

    @EntityGraph(attributePaths = {"account", "lines"})
    @Query("select i from Invoice i where i.uuid = :uuid")
    Optional<Invoice> findByUuid(@Param("uuid") UUID uuid);

    @EntityGraph(attributePaths = {"account", "lines"})
    @Query("select distinct i from Invoice i where i.account.uuid = :account order by i.createdAt, i.id")
    List<Invoice> findByAccount(@Param("account") UUID account);

    @Query("""
            select count(i) > 0 from Invoice i
            where i.account.uuid = :account
              and i.statusCode in (com.ClinicaDeYmid.billing_service.domain.InvoiceStatus.Code.DRAFT,
                                   com.ClinicaDeYmid.billing_service.domain.InvoiceStatus.Code.ISSUED)
              and (i.unitKind = com.ClinicaDeYmid.billing_service.domain.AccountSummary.UnitKind.ACCOUNT
                   or (:sale is not null and i.saleUuid = :sale))""")
    boolean liveFor(@Param("account") UUID account, @Param("sale") UUID sale);

    @Query("""
            select i.uuid from Invoice i
            where i.statusCode = com.ClinicaDeYmid.billing_service.domain.InvoiceStatus.Code.ISSUED
              and i.signedAt is null
            order by i.statusChangedAt, i.id""")
    List<UUID> awaitingSignature(Pageable page);
}
