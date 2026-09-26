package com.ClinicaDeYmid.billing_service.infrastructure.persistence;

import com.ClinicaDeYmid.billing_service.domain.CreditNote;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface CreditNoteJpaRepository extends JpaRepository<CreditNote, Long> {

    @EntityGraph(attributePaths = {"invoice", "lines"})
    @Query("select n from CreditNote n where n.uuid = :uuid")
    Optional<CreditNote> findByUuid(@Param("uuid") UUID uuid);

    @EntityGraph(attributePaths = {"lines"})
    @Query("select distinct n from CreditNote n where n.invoice.uuid = :invoice order by n.issuedAt, n.id")
    List<CreditNote> ofInvoice(@Param("invoice") UUID invoice);
}
