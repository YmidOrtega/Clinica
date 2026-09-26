package com.ClinicaDeYmid.billing_service.application.rips;

import com.ClinicaDeYmid.billing_service.application.DocumentAttachment;
import com.ClinicaDeYmid.billing_service.domain.BillingException;
import com.ClinicaDeYmid.billing_service.domain.ElectronicDocument;
import com.ClinicaDeYmid.billing_service.domain.ElectronicDocuments;
import com.ClinicaDeYmid.billing_service.domain.Invoice;
import com.ClinicaDeYmid.billing_service.domain.Invoices;
import com.ClinicaDeYmid.billing_service.domain.Issuers;
import com.ClinicaDeYmid.billing_service.domain.RipsSubmission;
import com.ClinicaDeYmid.billing_service.domain.RipsSubmissions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class MinistryValidation {

    private static final Logger log = LoggerFactory.getLogger(MinistryValidation.class);

    private final Invoices invoices;
    private final RipsSubmissions submissions;
    private final ElectronicDocuments documents;
    private final DocumentAttachment attachment;
    private final RipsQueries rips;
    private final Issuers issuers;
    private final MinistryValidator validator;
    private final TransactionOperations transactions;
    private final Clock clock;

    public MinistryValidation(Invoices invoices, RipsSubmissions submissions, ElectronicDocuments documents,
                              DocumentAttachment attachment, RipsQueries rips, Issuers issuers,
                              MinistryValidator validator, TransactionOperations transactions, Clock clock) {
        this.invoices = invoices;
        this.submissions = submissions;
        this.documents = documents;
        this.attachment = attachment;
        this.rips = rips;
        this.issuers = issuers;
        this.validator = validator;
        this.transactions = transactions;
        this.clock = clock;
    }

    public RipsSubmission submit(UUID invoiceUuid) {
        List<RipsSubmission> earlier = submissions.ofInvoice(invoiceUuid);
        Optional<RipsSubmission> open = earlier.stream()
                .filter(submission -> submission.status() != RipsSubmission.Status.REJECTED).findFirst();
        if (open.isPresent()) {
            if (open.get().status() == RipsSubmission.Status.VALIDATED) {
                throw new BillingException.RipsAlreadyValidated();
            }
            return send(open.get().uuid());
        }
        RipsDraft draft = rips.draft(invoiceUuid);
        if (!draft.complete()) {
            throw new BillingException.RipsIncomplete(draft.gaps());
        }
        ElectronicDocument document = documents.ofInvoice(invoiceUuid)
                .orElseThrow(BillingException.InvoiceNotIssued::new);
        attachment.attach(document.uuid());
        String json = validator.wireFormat(draft.document());
        RipsSubmission prepared;
        try {
            prepared = transactions.execute(status -> {
                Invoice invoice = invoices.findByUuid(invoiceUuid).orElseThrow(BillingException.InvoiceNotFound::new);
                return submissions.save(RipsSubmission.prepare(invoice, earlier.size() + 1, json));
            });
        } catch (DataIntegrityViolationException raced) {
            return submissions.ofInvoice(invoiceUuid).getFirst();
        }
        return send(prepared.uuid());
    }

    public RipsSubmission send(UUID submissionUuid) {
        RipsSubmission submission = submission(submissionUuid);
        if (submission.status() != RipsSubmission.Status.PENDING) {
            return submission;
        }
        Invoice invoice = submission.invoice();
        String nit = issuers.find().orElseThrow(BillingException.IssuerNotConfigured::new).nit().number();
        UUID document = documents.ofInvoice(invoice.uuid()).orElseThrow(BillingException.InvoiceNotIssued::new).uuid();
        String container = Base64.getEncoder().encodeToString(
                attachment.attach(document).content().getBytes(StandardCharsets.UTF_8));
        MinistryAnswer answer;
        MinistryAnswer recovered = null;
        try {
            answer = validator.submit(submission.rips(), container, nit);
            if (!answer.validated()) {
                Optional<String> earlierCuv = answer.cuvOfAnEarlierValidation();
                if (earlierCuv.isPresent()) {
                    MinistryAnswer recovery = validator.recover(earlierCuv.get(), nit);
                    if (recovery.validated() && invoice.number().equals(recovery.invoiceNumber())) {
                        recovered = recovery;
                    }
                }
            }
        } catch (BillingException.MinistryUnavailable unavailable) {
            transactions.executeWithoutResult(status -> {
                RipsSubmission current = submission(submissionUuid);
                current.unreachable("El mecanismo único de validación no respondió", clock.instant());
                submissions.save(current);
            });
            log.warn("RIPS of invoice {} stays pending: the ministry validator did not answer", invoice.number());
            throw unavailable;
        }
        MinistryAnswer outcome = recovered != null ? recovered : answer;
        boolean fromRecovery = recovered != null;
        RipsSubmission resolved = transactions.execute(status -> {
            RipsSubmission current = submission(submissionUuid);
            if (current.status() != RipsSubmission.Status.PENDING) {
                return current;
            }
            if (outcome.validated()) {
                current.validated(outcome.processId(), outcome.cuv(), outcome.filedAt(), fromRecovery,
                        outcome.findings(), outcome.raw(), clock.instant());
            } else {
                current.rejected(outcome.processId(), outcome.findings(), outcome.raw(), clock.instant());
            }
            return submissions.save(current);
        });
        log.info("RIPS of invoice {} {} by the ministry validator{}", invoice.number(), resolved.status(),
                fromRecovery ? " (CUV recovered)" : "");
        return resolved;
    }

    public int retryPending(int limit) {
        int sent = 0;
        for (UUID pending : submissions.pending(limit)) {
            try {
                send(pending);
                sent++;
            } catch (BillingException.MinistryUnavailable unavailable) {
                break;
            } catch (RuntimeException failed) {
                log.warn("RIPS submission {} stays pending: {}", pending, failed.getMessage());
            }
        }
        return sent;
    }

    public List<RipsSubmission> ofInvoice(UUID invoiceUuid) {
        invoices.findByUuid(invoiceUuid).orElseThrow(BillingException.InvoiceNotFound::new);
        return submissions.ofInvoice(invoiceUuid);
    }

    private RipsSubmission submission(UUID submissionUuid) {
        return submissions.findByUuid(submissionUuid)
                .orElseThrow(() -> new IllegalStateException("Unknown RIPS submission " + submissionUuid));
    }
}
