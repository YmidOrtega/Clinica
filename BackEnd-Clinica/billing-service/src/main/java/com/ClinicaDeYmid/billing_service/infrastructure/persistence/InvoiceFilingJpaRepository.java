package com.ClinicaDeYmid.billing_service.infrastructure.persistence;

import com.ClinicaDeYmid.billing_service.domain.Invoice;
import com.ClinicaDeYmid.billing_service.domain.InvoiceFiling;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface InvoiceFilingJpaRepository extends JpaRepository<InvoiceFiling, Long> {

    @EntityGraph(attributePaths = {"invoice"})
    @Query("select f from InvoiceFiling f where f.invoice.uuid = :invoice")
    Optional<InvoiceFiling> ofInvoice(@Param("invoice") UUID invoice);

    @Query("""
            select i from Invoice i join fetch i.account
            where i.purpose = com.ClinicaDeYmid.billing_service.domain.Invoice.Purpose.SERVICES
              and i.buyerKind = com.ClinicaDeYmid.billing_service.domain.Buyer.Kind.PAYER
              and i.statusCode = com.ClinicaDeYmid.billing_service.domain.InvoiceStatus.Code.ISSUED
              and (:payer is null or i.buyerReference = :payer)
              and not exists (select f.id from InvoiceFiling f where f.invoice = i)
            order by i.issuedOn, i.id""")
    List<Invoice> awaitingFiling(@Param("payer") UUID payer, Pageable page);
}
