package com.ClinicaDeYmid.billing_service.application;

import com.ClinicaDeYmid.billing_service.application.dian.DianAnswer;
import com.ClinicaDeYmid.billing_service.application.dian.DianGateway;
import com.ClinicaDeYmid.billing_service.application.dian.DianPackage;
import com.ClinicaDeYmid.billing_service.application.dian.DianReceipt;
import com.ClinicaDeYmid.billing_service.application.dian.DianSoftware;
import com.ClinicaDeYmid.billing_service.domain.BillingException;
import com.ClinicaDeYmid.billing_service.domain.DianEnvironment;
import com.ClinicaDeYmid.billing_service.domain.DianFileCounters;
import com.ClinicaDeYmid.billing_service.domain.DianStatus;
import com.ClinicaDeYmid.billing_service.domain.DianVerdict;
import com.ClinicaDeYmid.billing_service.domain.DianVerdicts;
import com.ClinicaDeYmid.billing_service.domain.Invoice;
import com.ClinicaDeYmid.billing_service.domain.InvoiceDocument;
import com.ClinicaDeYmid.billing_service.domain.InvoiceDocuments;
import com.ClinicaDeYmid.billing_service.domain.Invoices;
import com.ClinicaDeYmid.billing_service.domain.Issuer;
import com.ClinicaDeYmid.billing_service.domain.Issuers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
public class DianDelivery {

    private static final Logger log = LoggerFactory.getLogger(DianDelivery.class);

    private final Invoices invoices;
    private final InvoiceDocuments documents;
    private final DianVerdicts verdicts;
    private final DianFileCounters files;
    private final Issuers issuers;
    private final DianGateway dian;
    private final DianSoftware software;
    private final TransactionOperations transactions;
    private final Clock clock;

    public DianDelivery(Invoices invoices, InvoiceDocuments documents, DianVerdicts verdicts, DianFileCounters files,
                        Issuers issuers, DianGateway dian, DianSoftware software, TransactionOperations transactions,
                        Clock clock) {
        this.invoices = invoices;
        this.documents = documents;
        this.verdicts = verdicts;
        this.files = files;
        this.issuers = issuers;
        this.dian = dian;
        this.software = software;
        this.transactions = transactions;
        this.clock = clock;
    }

    public Invoice deliver(UUID invoiceUuid) {
        Invoice invoice = invoice(invoiceUuid);
        if (invoice.signedAt() == null || invoice.dianStatus() != null) {
            throw new BillingException.InvoiceNotDeliverable(
                    "Solo se envía a la DIAN una factura firmada que no esté en validación, aceptada ni rechazada");
        }
        Issuer issuer = issuers.find().orElseThrow(BillingException.IssuerNotConfigured::new);
        DianEnvironment environment = issuer.environment();
        String testSetId = environment == DianEnvironment.TEST ? software.requireTestSetId() : null;
        if (invoice.dianAttempts() > 0) {
            DianAnswer earlier = dian.statusOfDocument(environment, invoice.cufe());
            if (earlier.verdict() == DianAnswer.Verdict.ACCEPTED) {
                return record(invoiceUuid, DianVerdict.Operation.STATUS_OF_DOCUMENT, earlier);
            }
        }
        String signed = documents.find(invoiceUuid, InvoiceDocument.Kind.UBL_SIGNED)
                .orElseThrow(() -> new IllegalStateException("Signed invoice " + invoiceUuid + " has no signed UBL"))
                .content();
        DianPackage pack = transactions.execute(status -> {
            Invoice current = invoice(invoiceUuid);
            DianPackage built = DianPackage.of(DianPackage.baseName(issuer.nit(), LocalDate.now(clock).getYear(),
                    files.next(LocalDate.now(clock).getYear())), signed);
            current.attemptDianDelivery(built.zipName());
            invoices.save(current);
            return built;
        });
        if (environment == DianEnvironment.TEST) {
            DianReceipt receipt = dian.sendTestSet(environment, pack.zipName(), pack.zip(), testSetId);
            return transactions.execute(status -> {
                Invoice current = invoice(invoiceUuid);
                if (receipt.received()) {
                    current.awaitDianValidation(receipt.trackId(), clock.instant());
                } else {
                    current.rejectedByDian(clock.instant());
                }
                Invoice saved = invoices.save(current);
                verdicts.save(DianVerdict.of(saved, DianVerdict.Operation.SEND_TEST_SET,
                        receipt.received() ? DianVerdict.Outcome.RECEIVED : DianVerdict.Outcome.REJECTED, null, null,
                        receipt.errors(), clock.instant()));
                log.info("Invoice {} sent to the DIAN test set as {}: {}", saved.number(), pack.zipName(),
                        saved.dianStatus());
                return saved;
            });
        }
        return record(invoiceUuid, DianVerdict.Operation.SEND_BILL, dian.sendBill(environment, pack.zipName(), pack.zip()));
    }

    public Invoice checkValidation(UUID invoiceUuid) {
        Invoice invoice = invoice(invoiceUuid);
        if (invoice.dianStatus() != DianStatus.AWAITING_VALIDATION) {
            return invoice;
        }
        Issuer issuer = issuers.find().orElseThrow(BillingException.IssuerNotConfigured::new);
        if (issuer.environment() == DianEnvironment.TEST) {
            return record(invoiceUuid, DianVerdict.Operation.STATUS_OF_ZIP,
                    dian.statusOfZip(issuer.environment(), invoice.dianTrackId()));
        }
        return record(invoiceUuid, DianVerdict.Operation.STATUS_OF_DOCUMENT,
                dian.statusOfDocument(issuer.environment(), invoice.cufe()));
    }

    public Invoice sendNow(UUID invoiceUuid) {
        return invoice(invoiceUuid).dianStatus() == DianStatus.REJECTED ? resend(invoiceUuid) : deliver(invoiceUuid);
    }

    public Invoice resend(UUID invoiceUuid) {
        transactions.executeWithoutResult(status -> {
            Invoice invoice = invoice(invoiceUuid);
            invoice.requeueForDian();
            invoices.save(invoice);
        });
        try {
            return deliver(invoiceUuid);
        } catch (BillingException.DianUnavailable unavailable) {
            log.warn("Invoice {} requeued for the DIAN; the delivery will retry: {}", invoiceUuid,
                    unavailable.getMessage());
            return invoice(invoiceUuid);
        }
    }

    public List<DianVerdict> verdicts(UUID invoiceUuid) {
        invoice(invoiceUuid);
        return verdicts.ofInvoice(invoiceUuid);
    }

    public int deliverPending(int limit) {
        return each(invoices.awaitingDelivery(limit), this::deliver, "delivered to the DIAN");
    }

    public int checkPending(int limit) {
        return each(invoices.awaitingDianValidation(limit), this::checkValidation, "checked with the DIAN");
    }

    private int each(List<UUID> pending, java.util.function.Function<UUID, Invoice> step, String done) {
        int handled = 0;
        for (UUID invoiceUuid : pending) {
            try {
                step.apply(invoiceUuid);
                handled++;
            } catch (BillingException.DianUnavailable unavailable) {
                log.warn("The DIAN is unavailable; {} of {} invoices {}", handled, pending.size(), done);
                return handled;
            } catch (RuntimeException failed) {
                log.warn("Invoice {} could not be {}: {}", invoiceUuid, done, failed.getMessage());
            }
        }
        if (!pending.isEmpty()) {
            log.info("{} of {} invoices {}", handled, pending.size(), done);
        }
        return handled;
    }

    private Invoice record(UUID invoiceUuid, DianVerdict.Operation operation, DianAnswer answer) {
        return transactions.execute(status -> {
            Invoice current = invoice(invoiceUuid);
            DianVerdict.Outcome outcome = switch (answer.verdict()) {
                case PROCESSING -> {
                    if (current.dianStatus() == null) {
                        current.awaitDianValidation(current.cufe(), clock.instant());
                    }
                    yield DianVerdict.Outcome.PROCESSING;
                }
                case ACCEPTED -> {
                    current.acceptedByDian(clock.instant());
                    yield DianVerdict.Outcome.ACCEPTED;
                }
                case REJECTED -> {
                    current.rejectedByDian(clock.instant());
                    yield DianVerdict.Outcome.REJECTED;
                }
            };
            Invoice saved = invoices.save(current);
            verdicts.save(DianVerdict.of(saved, operation, outcome, answer.statusCode(), answer.statusDescription(),
                    answer.errors(), clock.instant()));
            if (outcome == DianVerdict.Outcome.ACCEPTED && answer.applicationResponse() != null
                    && documents.find(invoiceUuid, InvoiceDocument.Kind.DIAN_APPLICATION_RESPONSE).isEmpty()) {
                documents.save(InvoiceDocument.of(saved, InvoiceDocument.Kind.DIAN_APPLICATION_RESPONSE,
                        answer.applicationResponse()));
            }
            log.info("Invoice {} {} by the DIAN ({} {})", saved.number(), outcome, answer.statusCode(),
                    answer.statusDescription());
            return saved;
        });
    }

    private Invoice invoice(UUID invoiceUuid) {
        return invoices.findByUuid(invoiceUuid).orElseThrow(BillingException.InvoiceNotFound::new);
    }
}
