package com.ClinicaDeYmid.billing_service.infrastructure.events;

import com.ClinicaDeYmid.billing_service.application.InvoiceEvents;
import com.ClinicaDeYmid.billing_service.domain.CreditNote;
import com.ClinicaDeYmid.billing_service.domain.CreditNotes;
import com.ClinicaDeYmid.billing_service.domain.ElectronicDocument;
import com.ClinicaDeYmid.billing_service.domain.ElectronicDocuments;
import com.ClinicaDeYmid.billing_service.domain.Invoice;
import com.ClinicaDeYmid.billing_service.domain.InvoiceFilings;
import com.ClinicaDeYmid.billing_service.domain.InvoiceStatus;
import com.ClinicaDeYmid.billing_service.domain.Invoices;
import com.ClinicaDeYmid.billing_service.domain.PayerObjections;
import com.ClinicaDeYmid.billing_service.domain.RipsSubmissions;
import org.slf4j.MDC;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
class JdbcInvoiceEventOutbox implements InvoiceEvents {

    private final JdbcTemplate jdbc;
    private final Invoices invoices;
    private final ElectronicDocuments documents;
    private final CreditNotes notes;
    private final RipsSubmissions submissions;
    private final InvoiceFilings filings;
    private final PayerObjections objections;
    private final Clock clock;

    JdbcInvoiceEventOutbox(JdbcTemplate jdbc, Invoices invoices, ElectronicDocuments documents, CreditNotes notes,
                           RipsSubmissions submissions, InvoiceFilings filings, PayerObjections objections,
                           Clock clock) {
        this.jdbc = jdbc;
        this.invoices = invoices;
        this.documents = documents;
        this.notes = notes;
        this.submissions = submissions;
        this.filings = filings;
        this.objections = objections;
        this.clock = clock;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void invoiceChanged(UUID invoiceUuid, Change change) {
        Invoice invoice = invoices.findByUuid(invoiceUuid)
                .orElseThrow(() -> new IllegalStateException("Unknown invoice " + invoiceUuid));
        if (invoice.status() instanceof InvoiceStatus.Draft || invoice.status() instanceof InvoiceStatus.Discarded) {
            return;
        }
        List<CreditNote> credited = notes.ofInvoice(invoiceUuid);
        Map<UUID, ElectronicDocument> noteDocuments = new HashMap<>();
        credited.forEach(note -> documents.ofCreditNote(note.uuid())
                .ifPresent(document -> noteDocuments.put(note.uuid(), document)));
        UUID eventId = UUID.randomUUID();
        Instant occurredAt = Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
        InvoiceStateMessage message = InvoiceStateMessage.of(change.name(), invoice,
                documents.ofInvoice(invoiceUuid).orElse(null), credited, noteDocuments,
                submissions.validatedOf(List.of(invoiceUuid)).stream().findFirst().orElse(null),
                filings.ofInvoice(invoiceUuid).orElse(null), objections.ofInvoice(invoiceUuid), eventId, occurredAt,
                MDC.get("traceId"));
        jdbc.update(JdbcFilingAlertOutbox.INSERT, eventId.toString(), InvoiceStateMessage.AGGREGATE_TYPE,
                invoiceUuid.toString(), change.name(), JdbcFilingAlertOutbox.write(message),
                Timestamp.from(occurredAt));
    }
}
