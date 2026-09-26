package com.ClinicaDeYmid.billing_service.infrastructure.persistence;

import com.ClinicaDeYmid.billing_service.domain.InvoiceDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

interface InvoiceDocumentJpaRepository extends JpaRepository<InvoiceDocument, Long> {

    @Query("select d from InvoiceDocument d where d.invoice.uuid = :invoice and d.kind = :kind")
    Optional<InvoiceDocument> find(@Param("invoice") UUID invoice, @Param("kind") InvoiceDocument.Kind kind);
}
