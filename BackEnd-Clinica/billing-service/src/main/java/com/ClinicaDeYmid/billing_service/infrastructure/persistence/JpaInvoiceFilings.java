package com.ClinicaDeYmid.billing_service.infrastructure.persistence;

import com.ClinicaDeYmid.billing_service.domain.Invoice;
import com.ClinicaDeYmid.billing_service.domain.InvoiceFiling;
import com.ClinicaDeYmid.billing_service.domain.InvoiceFilings;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
class JpaInvoiceFilings implements InvoiceFilings {

    private final InvoiceFilingJpaRepository repository;

    JpaInvoiceFilings(InvoiceFilingJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public InvoiceFiling save(InvoiceFiling filing) {
        return repository.saveAndFlush(filing);
    }

    @Override
    public Optional<InvoiceFiling> ofInvoice(UUID invoiceUuid) {
        return repository.ofInvoice(invoiceUuid);
    }

    @Override
    public List<Invoice> awaitingFiling(UUID payerUuid, int limit) {
        return repository.awaitingFiling(payerUuid, PageRequest.of(0, limit));
    }
}
