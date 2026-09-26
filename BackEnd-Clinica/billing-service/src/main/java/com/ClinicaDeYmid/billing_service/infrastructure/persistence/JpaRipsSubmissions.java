package com.ClinicaDeYmid.billing_service.infrastructure.persistence;

import com.ClinicaDeYmid.billing_service.domain.RipsSubmission;
import com.ClinicaDeYmid.billing_service.domain.RipsSubmissions;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
class JpaRipsSubmissions implements RipsSubmissions {

    private final RipsSubmissionJpaRepository repository;

    JpaRipsSubmissions(RipsSubmissionJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public RipsSubmission save(RipsSubmission submission) {
        return repository.saveAndFlush(submission);
    }

    @Override
    public Optional<RipsSubmission> findByUuid(UUID uuid) {
        return repository.findByUuid(uuid);
    }

    @Override
    public List<RipsSubmission> ofInvoice(UUID invoiceUuid) {
        return repository.ofInvoice(invoiceUuid);
    }

    @Override
    public List<UUID> pending(int limit) {
        return repository.pending(PageRequest.of(0, limit));
    }
}
