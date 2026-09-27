package com.ClinicaDeYmid.ai_assistant_service.service;

import com.ClinicaDeYmid.ai_assistant_service.repository.InvoiceSnapshotRepository;
import com.ClinicaDeYmid.ai_assistant_service.repository.entity.InvoiceSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InvoiceProjection {

    private static final Logger log = LoggerFactory.getLogger(InvoiceProjection.class);

    private final InvoiceSnapshotRepository invoices;
    private final FindingService findings;

    public InvoiceProjection(InvoiceSnapshotRepository invoices, FindingService findings) {
        this.invoices = invoices;
        this.findings = findings;
    }

    @Transactional
    public boolean follow(InvoiceState state) {
        InvoiceSnapshot snapshot = invoices.findByInvoiceUuid(state.invoiceUuid())
                .orElseGet(() -> InvoiceSnapshot.of(state.invoiceUuid()));
        if (!snapshot.isNewerThanWhatIHave(state.occurredAt())) {
            log.debug("Invoice {} already at {}; ignored {}", state.number(), snapshot.lastEventAt(), state.eventType());
            return false;
        }
        snapshot.follow(state.eventType(), state.occurredAt(), state.number(), state.purpose(), state.status(),
                state.issuedOn(), state.admissionNumber(), state.buyerKind(), state.payerNit(),
                state.contractNumber(), state.uncontractedCare(), state.payableTotal(), state.creditedTotal(),
                state.balance(), state.shareShortfall(), state.dianStatus(), state.dianStatusAt(), state.cuv(),
                state.filingNumber(), state.filedOn(), state.json());
        findings.review(invoices.save(snapshot));
        log.debug("Invoice {} now at {}", state.number(), state.eventType());
        return true;
    }
}
