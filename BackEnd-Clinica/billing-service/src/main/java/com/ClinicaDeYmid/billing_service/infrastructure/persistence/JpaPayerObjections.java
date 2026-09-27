package com.ClinicaDeYmid.billing_service.infrastructure.persistence;

import com.ClinicaDeYmid.billing_service.domain.PayerObjection;
import com.ClinicaDeYmid.billing_service.domain.PayerObjections;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
class JpaPayerObjections implements PayerObjections {

    private final PayerObjectionJpaRepository repository;

    JpaPayerObjections(PayerObjectionJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public PayerObjection save(PayerObjection objection) {
        return repository.saveAndFlush(objection);
    }

    @Override
    public Optional<PayerObjection> findByUuid(UUID uuid) {
        return repository.findByUuid(uuid);
    }

    @Override
    public List<PayerObjection> ofInvoice(UUID invoiceUuid) {
        return repository.ofInvoice(invoiceUuid);
    }

    @Override
    public List<PayerObjection> awaitingResponse(UUID payerUuid, int limit) {
        return repository.awaitingResponse(payerUuid, PageRequest.of(0, limit));
    }
}
