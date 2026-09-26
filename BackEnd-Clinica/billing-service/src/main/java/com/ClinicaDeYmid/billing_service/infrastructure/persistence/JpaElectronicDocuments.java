package com.ClinicaDeYmid.billing_service.infrastructure.persistence;

import com.ClinicaDeYmid.billing_service.domain.ElectronicDocument;
import com.ClinicaDeYmid.billing_service.domain.ElectronicDocuments;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
class JpaElectronicDocuments implements ElectronicDocuments {

    private final ElectronicDocumentJpaRepository repository;

    JpaElectronicDocuments(ElectronicDocumentJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public ElectronicDocument save(ElectronicDocument document) {
        return repository.saveAndFlush(document);
    }

    @Override
    public Optional<ElectronicDocument> findByUuid(UUID uuid) {
        return repository.findByUuid(uuid);
    }

    @Override
    public Optional<ElectronicDocument> ofInvoice(UUID invoiceUuid) {
        return repository.ofInvoice(invoiceUuid);
    }

    @Override
    public Optional<ElectronicDocument> ofCreditNote(UUID creditNoteUuid) {
        return repository.ofCreditNote(creditNoteUuid);
    }

    @Override
    public List<UUID> awaitingSignature(int limit) {
        return repository.awaitingSignature(PageRequest.of(0, limit));
    }

    @Override
    public List<UUID> awaitingDelivery(int limit) {
        return repository.awaitingDelivery(PageRequest.of(0, limit));
    }

    @Override
    public List<UUID> awaitingDianValidation(int limit) {
        return repository.awaitingDianValidation(PageRequest.of(0, limit));
    }

    @Override
    public List<UUID> awaitingAttachment(int limit) {
        return repository.awaitingAttachment(PageRequest.of(0, limit));
    }
}
