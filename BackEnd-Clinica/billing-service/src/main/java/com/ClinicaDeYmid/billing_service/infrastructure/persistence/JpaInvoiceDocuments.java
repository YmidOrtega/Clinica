package com.ClinicaDeYmid.billing_service.infrastructure.persistence;

import com.ClinicaDeYmid.billing_service.domain.InvoiceDocument;
import com.ClinicaDeYmid.billing_service.domain.InvoiceDocuments;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
class JpaInvoiceDocuments implements InvoiceDocuments {

    private final InvoiceDocumentJpaRepository repository;

    JpaInvoiceDocuments(InvoiceDocumentJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public InvoiceDocument save(InvoiceDocument document) {
        return repository.saveAndFlush(document);
    }

    @Override
    public Optional<InvoiceDocument> find(UUID invoiceUuid, InvoiceDocument.Kind kind) {
        return repository.find(invoiceUuid, kind);
    }
}
