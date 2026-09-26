package com.ClinicaDeYmid.billing_service.infrastructure.persistence;

import com.ClinicaDeYmid.billing_service.domain.CreditNote;
import com.ClinicaDeYmid.billing_service.domain.CreditNotes;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
class JpaCreditNotes implements CreditNotes {

    private final CreditNoteJpaRepository repository;

    JpaCreditNotes(CreditNoteJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public CreditNote save(CreditNote note) {
        return repository.saveAndFlush(note);
    }

    @Override
    public Optional<CreditNote> findByUuid(UUID uuid) {
        return repository.findByUuid(uuid);
    }

    @Override
    public List<CreditNote> ofInvoice(UUID invoiceUuid) {
        return repository.ofInvoice(invoiceUuid);
    }
}
