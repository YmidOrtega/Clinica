package com.ClinicaDeYmid.billing_service.application.filing;

import com.ClinicaDeYmid.billing_service.domain.BillingException;
import com.ClinicaDeYmid.billing_service.domain.FilingDeadline;
import com.ClinicaDeYmid.billing_service.domain.Invoice;
import com.ClinicaDeYmid.billing_service.domain.InvoiceFiling;
import com.ClinicaDeYmid.billing_service.domain.InvoiceFilings;
import com.ClinicaDeYmid.billing_service.domain.Invoices;
import com.ClinicaDeYmid.billing_service.domain.RipsSubmission;
import com.ClinicaDeYmid.billing_service.domain.RipsSubmissions;
import com.ClinicaDeYmid.commons.web.EntityTags;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class InvoiceFilingService {

    private static final Logger log = LoggerFactory.getLogger(InvoiceFilingService.class);

    private final Invoices invoices;
    private final InvoiceFilings filings;
    private final RipsSubmissions submissions;
    private final FilingPolicy policy;
    private final TransactionOperations transactions;
    private final Clock clock;

    public InvoiceFilingService(Invoices invoices, InvoiceFilings filings, RipsSubmissions submissions,
                                FilingPolicy policy, TransactionOperations transactions, Clock clock) {
        this.invoices = invoices;
        this.filings = filings;
        this.submissions = submissions;
        this.policy = policy;
        this.transactions = transactions;
        this.clock = clock;
    }

    public InvoiceFiling register(UUID invoiceUuid, String filingNumber, LocalDate filedOn) {
        try {
            InvoiceFiling filing = transactions.execute(status -> {
                InvoiceFiling.requireFileable(invoice(invoiceUuid));
                if (filings.ofInvoice(invoiceUuid).isPresent()) {
                    throw new BillingException.AlreadyFiled();
                }
                RipsSubmission validated = validated(invoiceUuid);
                return filings.save(InvoiceFiling.register(validated, filingNumber, filedOn, validatedOn(validated),
                        LocalDate.now(clock)));
            });
            log.info("Invoice {} filed with the payer as {} on {}{}", filing.invoice().number(), filing.filingNumber(),
                    filing.filedOn(), filing.late() ? " after its deadline " + filing.deadline() : "");
            return filing;
        } catch (DataIntegrityViolationException raced) {
            throw new BillingException.AlreadyFiled();
        }
    }

    public InvoiceFiling correct(UUID invoiceUuid, long expectedVersion, String filingNumber, LocalDate filedOn,
                                 String reason) {
        return transactions.execute(status -> {
            InvoiceFiling filing = filings.ofInvoice(invoiceUuid).orElseThrow(BillingException.FilingNotFound::new);
            if (filing.version() != expectedVersion) {
                throw new EntityTags.StaleVersion();
            }
            filing.correct(filingNumber, filedOn, reason, validatedOn(validated(invoiceUuid)), LocalDate.now(clock));
            return filings.save(filing);
        });
    }

    public FilingStatus status(UUID invoiceUuid) {
        Invoice invoice = invoice(invoiceUuid);
        InvoiceFiling filing = filings.ofInvoice(invoiceUuid).orElse(null);
        if (filing == null) {
            InvoiceFiling.requireFileable(invoice);
        }
        String cuv = submissions.validatedOf(List.of(invoiceUuid)).stream().findFirst()
                .map(RipsSubmission::cuv).orElse(null);
        return new FilingStatus(invoice, filing, filing == null ? deadlineOf(invoice) : null, cuv);
    }

    public List<FilingStatus> tray(UUID payerUuid, FilingDeadline.State state, int limit) {
        List<Invoice> awaiting = filings.awaitingFiling(payerUuid, limit);
        Map<UUID, String> cuvs = submissions.validatedOf(awaiting.stream().map(Invoice::uuid).toList()).stream()
                .collect(Collectors.toMap(submission -> submission.invoice().uuid(), RipsSubmission::cuv));
        return awaiting.stream()
                .map(invoice -> new FilingStatus(invoice, null, deadlineOf(invoice), cuvs.get(invoice.uuid())))
                .filter(status -> state == null || status.deadline().state() == state)
                .toList();
    }

    public FilingDeadline deadlineOf(Invoice invoice) {
        return FilingDeadline.of(invoice.issuedOn(), LocalDate.now(clock), policy.warningBusinessDays());
    }

    private RipsSubmission validated(UUID invoiceUuid) {
        return submissions.validatedOf(List.of(invoiceUuid)).stream().findFirst()
                .orElseThrow(BillingException.FilingWithoutCuv::new);
    }

    private LocalDate validatedOn(RipsSubmission validated) {
        return LocalDate.ofInstant(validated.resolvedAt(), clock.getZone());
    }

    private Invoice invoice(UUID invoiceUuid) {
        return invoices.findByUuid(invoiceUuid).orElseThrow(BillingException.InvoiceNotFound::new);
    }
}
