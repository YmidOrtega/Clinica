package com.ClinicaDeYmid.billing_service.infrastructure.persistence;

import com.ClinicaDeYmid.billing_service.domain.PayerObjection;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface PayerObjectionJpaRepository extends JpaRepository<PayerObjection, Long> {

    @EntityGraph(attributePaths = {"invoice", "invoice.account", "items", "creditNote"})
    @Query("select o from PayerObjection o where o.uuid = :uuid")
    Optional<PayerObjection> findByUuid(@Param("uuid") UUID uuid);

    @EntityGraph(attributePaths = {"invoice", "invoice.account", "items", "creditNote"})
    @Query("select distinct o from PayerObjection o where o.invoice.uuid = :invoice order by o.notifiedOn, o.id")
    List<PayerObjection> ofInvoice(@Param("invoice") UUID invoice);

    @EntityGraph(attributePaths = {"invoice", "invoice.account", "items"})
    @Query("""
            select distinct o from PayerObjection o
            where o.status = com.ClinicaDeYmid.billing_service.domain.PayerObjection.Status.AWAITING_RESPONSE
              and (:payer is null or o.invoice.buyerReference = :payer)
            order by o.responseDeadline, o.id""")
    List<PayerObjection> awaitingResponse(@Param("payer") UUID payer, Pageable page);
}
