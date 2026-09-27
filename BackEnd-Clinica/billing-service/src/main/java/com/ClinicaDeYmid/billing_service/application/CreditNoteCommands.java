package com.ClinicaDeYmid.billing_service.application;

import com.ClinicaDeYmid.billing_service.application.dian.DianSoftware;
import com.ClinicaDeYmid.billing_service.application.dian.ElectronicCreditNote;
import com.ClinicaDeYmid.billing_service.application.dian.UblWriter;
import com.ClinicaDeYmid.billing_service.domain.BillingException;
import com.ClinicaDeYmid.billing_service.domain.CreditConcept;
import com.ClinicaDeYmid.billing_service.domain.CreditNote;
import com.ClinicaDeYmid.billing_service.domain.CreditNoteCounters;
import com.ClinicaDeYmid.billing_service.domain.CreditNotes;
import com.ClinicaDeYmid.billing_service.domain.CreditRequest;
import com.ClinicaDeYmid.billing_service.domain.Cufe;
import com.ClinicaDeYmid.billing_service.domain.DianStatus;
import com.ClinicaDeYmid.billing_service.domain.DocumentFile;
import com.ClinicaDeYmid.billing_service.domain.DocumentFiles;
import com.ClinicaDeYmid.billing_service.domain.ElectronicDocument;
import com.ClinicaDeYmid.billing_service.domain.ElectronicDocuments;
import com.ClinicaDeYmid.billing_service.domain.Invoice;
import com.ClinicaDeYmid.billing_service.domain.Invoices;
import com.ClinicaDeYmid.billing_service.domain.Issuer;
import com.ClinicaDeYmid.billing_service.domain.Issuers;
import com.ClinicaDeYmid.commons.web.EntityTags;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

@Service
public class CreditNoteCommands {

    private static final Logger log = LoggerFactory.getLogger(CreditNoteCommands.class);

    private final Invoices invoices;
    private final CreditNotes notes;
    private final CreditNoteCounters counters;
    private final ElectronicDocuments documents;
    private final DocumentFiles files;
    private final Issuers issuers;
    private final UblWriter ubl;
    private final DianSoftware software;
    private final InvoiceEvents events;
    private final TransactionOperations transactions;
    private final Clock clock;

    public CreditNoteCommands(Invoices invoices, CreditNotes notes, CreditNoteCounters counters,
                              ElectronicDocuments documents, DocumentFiles files, Issuers issuers, UblWriter ubl,
                              DianSoftware software, InvoiceEvents events, TransactionOperations transactions,
                              Clock clock) {
        this.invoices = invoices;
        this.notes = notes;
        this.counters = counters;
        this.documents = documents;
        this.files = files;
        this.issuers = issuers;
        this.ubl = ubl;
        this.software = software;
        this.transactions = transactions;
        this.events = events;
        this.clock = clock;
    }

    public ElectronicDocument issue(UUID invoiceUuid, long expectedInvoiceVersion, CreditConcept concept, String reason,
                                    List<CreditRequest> requests) {
        String pin = software.requireConfigured().pin();
        ElectronicDocument issued = transactions.execute(status -> {
            Invoice invoice = invoices.findByUuid(invoiceUuid).orElseThrow(BillingException.InvoiceNotFound::new);
            if (invoice.version() != expectedInvoiceVersion) {
                throw new EntityTags.StaleVersion();
            }
            ElectronicDocument credited = documents.ofInvoice(invoiceUuid).orElseThrow(() ->
                    new BillingException.InvoiceNotCreditable("Solo se acredita una factura emitida"));
            if (credited.dianStatus() != DianStatus.ACCEPTED) {
                throw new BillingException.InvoiceNotCreditable(
                        "La DIAN aún no ha aceptado la factura; la nota crédito debe referenciar una factura válida");
            }
            Issuer issuer = issuers.find().orElseThrow(BillingException.IssuerNotConfigured::new);
            String prefix = issuer.creditNotePrefix();
            CreditNote note = CreditNote.issue(invoice, notes.ofInvoice(invoiceUuid), concept, reason, requests,
                    prefix, counters.next(prefix), clock);
            note.identify(Cufe.of(note.cudeInput(issuer, pin)));
            invoice.credit(note);
            invoices.save(invoice);
            CreditNote saved = notes.save(note);
            ElectronicDocument document = documents.save(ElectronicDocument.ofCreditNote(saved));
            files.save(DocumentFile.of(document, DocumentFile.Kind.UBL_UNSIGNED,
                    ubl.creditNote(ElectronicCreditNote.of(saved, issuer, software))));
            events.invoiceChanged(invoiceUuid, concept == CreditConcept.VOID ? InvoiceEvents.Change.InvoiceVoided
                    : InvoiceEvents.Change.InvoiceCredited);
            return document;
        });
        log.info("Credit note {} ({}) issued on invoice {}", issued.number(), concept, issued.invoice().number());
        return issued;
    }
}
