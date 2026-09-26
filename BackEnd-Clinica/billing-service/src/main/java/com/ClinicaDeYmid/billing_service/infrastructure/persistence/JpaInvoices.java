package com.ClinicaDeYmid.billing_service.infrastructure.persistence;

import com.ClinicaDeYmid.billing_service.domain.Invoice;
import com.ClinicaDeYmid.billing_service.domain.Invoices;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
class JpaInvoices implements Invoices {

    private final InvoiceJpaRepository repository;

    JpaInvoices(InvoiceJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public Invoice save(Invoice invoice) {
        return repository.saveAndFlush(invoice);
    }

    @Override
    public Optional<Invoice> findByUuid(UUID uuid) {
        return repository.findByUuid(uuid);
    }

    @Override
    public List<Invoice> findByAccount(UUID accountUuid) {
        return repository.findByAccount(accountUuid);
    }

    @Override
    public boolean liveFor(UUID accountUuid, UUID saleUuid) {
        return repository.liveFor(accountUuid, saleUuid);
    }

    @Override
    public List<Invoice> sharedPaymentsOf(UUID accountUuid) {
        return repository.sharedPaymentsOf(accountUuid);
    }

    @Override
    public Optional<Invoice> findByCollectionReference(String collectionReference) {
        return repository.findByCollectionReference(collectionReference);
    }
}
