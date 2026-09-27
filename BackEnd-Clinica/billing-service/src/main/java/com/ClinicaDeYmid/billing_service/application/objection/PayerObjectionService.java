package com.ClinicaDeYmid.billing_service.application.objection;

import com.ClinicaDeYmid.billing_service.application.CreditNoteCommands;
import com.ClinicaDeYmid.billing_service.application.InvoiceEvents;
import com.ClinicaDeYmid.billing_service.domain.BillingException;
import com.ClinicaDeYmid.billing_service.domain.BusinessDeadline;
import com.ClinicaDeYmid.billing_service.domain.CreditConcept;
import com.ClinicaDeYmid.billing_service.domain.ElectronicDocument;
import com.ClinicaDeYmid.billing_service.domain.Invoice;
import com.ClinicaDeYmid.billing_service.domain.InvoiceFilings;
import com.ClinicaDeYmid.billing_service.domain.Invoices;
import com.ClinicaDeYmid.billing_service.domain.ObjectionCatalog;
import com.ClinicaDeYmid.billing_service.domain.PayerObjection;
import com.ClinicaDeYmid.billing_service.domain.PayerObjections;
import com.ClinicaDeYmid.commons.web.EntityTags;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
public class PayerObjectionService {

    private static final Logger log = LoggerFactory.getLogger(PayerObjectionService.class);

    private final Invoices invoices;
    private final InvoiceFilings filings;
    private final PayerObjections objections;
    private final ObjectionCatalog catalog;
    private final CreditNoteCommands creditNotes;
    private final ObjectionPolicy policy;
    private final InvoiceEvents events;
    private final TransactionOperations transactions;
    private final Clock clock;

    public PayerObjectionService(Invoices invoices, InvoiceFilings filings, PayerObjections objections,
                                 ObjectionCatalog catalog, CreditNoteCommands creditNotes, ObjectionPolicy policy,
                                 InvoiceEvents events, TransactionOperations transactions, Clock clock) {
        this.invoices = invoices;
        this.filings = filings;
        this.objections = objections;
        this.catalog = catalog;
        this.creditNotes = creditNotes;
        this.policy = policy;
        this.events = events;
        this.transactions = transactions;
        this.clock = clock;
    }

    public record Responded(PayerObjection objection, ElectronicDocument creditNote) {
    }

    public PayerObjection register(UUID invoiceUuid, PayerObjection.Kind kind, String payerRecord, LocalDate notifiedOn,
                                   List<PayerObjection.Item> items) {
        try {
            PayerObjection registered = transactions.execute(status -> {
                invoices.findByUuid(invoiceUuid).orElseThrow(BillingException.InvoiceNotFound::new);
                PayerObjection saved = objections.save(PayerObjection.register(
                        filings.ofInvoice(invoiceUuid).orElse(null), kind, payerRecord, notifiedOn, items,
                        objections.ofInvoice(invoiceUuid), catalog::find, LocalDate.now(clock)));
                events.invoiceChanged(invoiceUuid, InvoiceEvents.Change.InvoiceObjectionRegistered);
                return saved;
            });
            log.info("{} {} of invoice {} registered for {}{}", kind, registered.payerRecord(),
                    registered.invoice().number(), registered.claimedAmount(),
                    registered.extemporaneous() ? " after its formulation deadline" : "");
            return objections.findByUuid(registered.uuid()).orElseThrow();
        } catch (DataIntegrityViolationException twice) {
            throw new BillingException.InvalidObjection("El pagador solo puede devolver una factura una vez");
        }
    }

    public Responded respond(UUID objectionUuid, long expectedVersion, String responseRecord, LocalDate respondedOn,
                             List<PayerObjection.Answer> answers) {
        Responded responded = transactions.execute(status -> {
            PayerObjection objection = current(objectionUuid, expectedVersion);
            objection.respond(responseRecord, respondedOn, answers, LocalDate.now(clock));
            ElectronicDocument note = null;
            if (objection.acceptedAmount().signum() > 0) {
                Invoice invoice = invoices.findByUuid(objection.invoice().uuid())
                        .orElseThrow(BillingException.InvoiceNotFound::new);
                boolean devolution = objection.kind() == PayerObjection.Kind.DEVOLUTION;
                note = creditNotes.issue(invoice.uuid(), invoice.version(),
                        devolution ? CreditConcept.VOID : CreditConcept.PARTIAL_RETURN,
                        (devolution ? "Devolución " : "Glosa ") + objection.payerRecord() + " aceptada ("
                                + objection.items().getFirst().responseCode() + ")",
                        devolution ? List.of() : objection.acceptedByLine());
                objection.settledBy(note.creditNote());
            }
            PayerObjection saved = objections.save(objection);
            events.invoiceChanged(saved.invoice().uuid(), InvoiceEvents.Change.InvoiceObjectionAnswered);
            return new Responded(saved, note);
        });
        log.info("{} {} answered with {} accepted{}", responded.objection().kind(), responded.objection().payerRecord(),
                responded.objection().acceptedAmount(),
                responded.creditNote() == null ? "" : " by credit note " + responded.creditNote().number());
        return new Responded(objections.findByUuid(objectionUuid).orElseThrow(), responded.creditNote());
    }

    public PayerObjection decide(UUID objectionUuid, long expectedVersion, LocalDate decidedOn,
                                 List<PayerObjection.Ruling> rulings) {
        transactions.executeWithoutResult(status -> {
            PayerObjection objection = current(objectionUuid, expectedVersion);
            objection.decide(decidedOn, rulings, LocalDate.now(clock));
            PayerObjection saved = objections.save(objection);
            events.invoiceChanged(saved.invoice().uuid(), InvoiceEvents.Change.InvoiceObjectionDecided);
        });
        return objections.findByUuid(objectionUuid).orElseThrow();
    }

    public ObjectionStatus status(UUID objectionUuid) {
        return statusOf(objections.findByUuid(objectionUuid).orElseThrow(BillingException.ObjectionNotFound::new));
    }

    public List<ObjectionStatus> ofInvoice(UUID invoiceUuid) {
        invoices.findByUuid(invoiceUuid).orElseThrow(BillingException.InvoiceNotFound::new);
        return objections.ofInvoice(invoiceUuid).stream().map(this::statusOf).toList();
    }

    public List<ObjectionStatus> tray(UUID payerUuid, BusinessDeadline.State state, int limit) {
        return objections.awaitingResponse(payerUuid, limit).stream().map(this::statusOf)
                .filter(found -> state == null || found.responseDue().state() == state)
                .toList();
    }

    private ObjectionStatus statusOf(PayerObjection objection) {
        BusinessDeadline due = objection.status() != PayerObjection.Status.AWAITING_RESPONSE ? null
                : BusinessDeadline.of(objection.notifiedOn(), objection.kind().daysToRespond(), LocalDate.now(clock),
                policy.warningBusinessDays());
        return new ObjectionStatus(objection, due);
    }

    private PayerObjection current(UUID objectionUuid, long expectedVersion) {
        PayerObjection objection = objections.findByUuid(objectionUuid)
                .orElseThrow(BillingException.ObjectionNotFound::new);
        if (objection.version() != expectedVersion) {
            throw new EntityTags.StaleVersion();
        }
        return objection;
    }
}
