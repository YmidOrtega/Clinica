package com.ClinicaDeYmid.contracting_service.application;

import com.ClinicaDeYmid.contracting_service.domain.ContractingException;
import com.ClinicaDeYmid.contracting_service.domain.Nit;
import com.ClinicaDeYmid.contracting_service.domain.Payer;
import com.ClinicaDeYmid.contracting_service.domain.Payers;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class PayerQueries {

    private final Payers payers;
    private final PayerHistory history;

    public PayerQueries(Payers payers, PayerHistory history) {
        this.payers = payers;
        this.history = history;
    }

    public Payer get(UUID uuid) {
        return payers.findByUuid(uuid).orElseThrow(ContractingException.PayerNotFound::new);
    }

    public Optional<Payer> findByNit(Nit nit) {
        return payers.findByNit(nit);
    }

    public Page searchBySocialReason(String prefix, int page, int size) {
        return new Page(payers.searchBySocialReason(prefix, page, size), payers.countBySocialReason(prefix));
    }

    public List<PayerHistory.Revision> history(UUID uuid) {
        List<PayerHistory.Revision> revisions = history.of(uuid);
        if (revisions.isEmpty()) {
            throw new ContractingException.PayerNotFound();
        }
        return revisions;
    }

    public record Page(List<Payer> matches, long total) {
    }
}
