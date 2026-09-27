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
import com.ClinicaDeYmid.billing_service.domain.DocumentFile;
import com.ClinicaDeYmid.billing_service.domain.DocumentFiles;
import com.ClinicaDeYmid.billing_service.domain.ElectronicDocument;
import com.ClinicaDeYmid.billing_service.domain.ElectronicDocuments;
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
import java.util.function.Function;

@Service
public class DianDelivery {

    private static final Logger log = LoggerFactory.getLogger(DianDelivery.class);

    private final ElectronicDocuments documents;
    private final DocumentFiles files;
    private final DianVerdicts verdicts;
    private final DianFileCounters counters;
    private final Issuers issuers;
    private final DianGateway dian;
    private final DianSoftware software;
    private final InvoiceEvents events;
    private final TransactionOperations transactions;
    private final Clock clock;

    public DianDelivery(ElectronicDocuments documents, DocumentFiles files, DianVerdicts verdicts,
                        DianFileCounters counters, Issuers issuers, DianGateway dian, DianSoftware software,
                        InvoiceEvents events, TransactionOperations transactions, Clock clock) {
        this.documents = documents;
        this.files = files;
        this.verdicts = verdicts;
        this.counters = counters;
        this.issuers = issuers;
        this.dian = dian;
        this.software = software;
        this.events = events;
        this.transactions = transactions;
        this.clock = clock;
    }

    public ElectronicDocument deliver(UUID documentUuid) {
        ElectronicDocument document = document(documentUuid);
        if (document.signedAt() == null || document.dianStatus() != null) {
            throw new BillingException.DocumentNotDeliverable(
                    "Solo se envía a la DIAN un documento firmado que no esté en validación, aceptado ni rechazado");
        }
        Issuer issuer = issuers.find().orElseThrow(BillingException.IssuerNotConfigured::new);
        DianEnvironment environment = issuer.environment();
        String testSetId = environment == DianEnvironment.TEST ? software.requireTestSetId() : null;
        if (document.dianAttempts() > 0) {
            DianAnswer earlier = dian.statusOfDocument(environment, document.documentKey());
            if (earlier.verdict() == DianAnswer.Verdict.ACCEPTED) {
                return record(documentUuid, DianVerdict.Operation.STATUS_OF_DOCUMENT, earlier);
            }
        }
        String signed = files.find(documentUuid, DocumentFile.Kind.UBL_SIGNED)
                .orElseThrow(() -> new IllegalStateException("Signed document " + documentUuid + " has no signed UBL"))
                .content();
        DianPackage pack = transactions.execute(status -> {
            ElectronicDocument current = document(documentUuid);
            int year = LocalDate.now(clock).getYear();
            DianPackage built = DianPackage.of(current.type().filePrefix(),
                    DianPackage.baseName(issuer.nit(), year, counters.next(year)), signed);
            current.attemptDianDelivery(built.zipName());
            documents.save(current);
            return built;
        });
        if (environment == DianEnvironment.TEST) {
            DianReceipt receipt = dian.sendTestSet(environment, pack.zipName(), pack.zip(), testSetId);
            return transactions.execute(status -> {
                ElectronicDocument current = document(documentUuid);
                if (receipt.received()) {
                    current.awaitDianValidation(receipt.trackId(), clock.instant());
                } else {
                    current.rejectedByDian(clock.instant());
                }
                ElectronicDocument saved = documents.save(current);
                verdicts.save(DianVerdict.of(saved, DianVerdict.Operation.SEND_TEST_SET,
                        receipt.received() ? DianVerdict.Outcome.RECEIVED : DianVerdict.Outcome.REJECTED, null, null,
                        receipt.errors(), clock.instant()));
                log.info("{} {} sent to the DIAN test set as {}: {}", saved.type(), saved.number(), pack.zipName(),
                        saved.dianStatus());
                return saved;
            });
        }
        return record(documentUuid, DianVerdict.Operation.SEND_BILL,
                dian.sendBill(environment, pack.zipName(), pack.zip()));
    }

    public ElectronicDocument checkValidation(UUID documentUuid) {
        ElectronicDocument document = document(documentUuid);
        if (document.dianStatus() != DianStatus.AWAITING_VALIDATION) {
            return document;
        }
        Issuer issuer = issuers.find().orElseThrow(BillingException.IssuerNotConfigured::new);
        if (issuer.environment() == DianEnvironment.TEST) {
            return record(documentUuid, DianVerdict.Operation.STATUS_OF_ZIP,
                    dian.statusOfZip(issuer.environment(), document.dianTrackId()));
        }
        return record(documentUuid, DianVerdict.Operation.STATUS_OF_DOCUMENT,
                dian.statusOfDocument(issuer.environment(), document.documentKey()));
    }

    public ElectronicDocument sendNow(UUID documentUuid) {
        return document(documentUuid).dianStatus() == DianStatus.REJECTED ? resend(documentUuid) : deliver(documentUuid);
    }

    public ElectronicDocument resend(UUID documentUuid) {
        transactions.executeWithoutResult(status -> {
            ElectronicDocument document = document(documentUuid);
            document.requeueForDian();
            documents.save(document);
        });
        try {
            return deliver(documentUuid);
        } catch (BillingException.DianUnavailable unavailable) {
            log.warn("Electronic document {} requeued for the DIAN; the delivery will retry: {}", documentUuid,
                    unavailable.getMessage());
            return document(documentUuid);
        }
    }

    public List<DianVerdict> verdicts(UUID documentUuid) {
        document(documentUuid);
        return verdicts.ofDocument(documentUuid);
    }

    public int deliverPending(int limit) {
        return each(documents.awaitingDelivery(limit), this::deliver, "delivered to the DIAN");
    }

    public int checkPending(int limit) {
        return each(documents.awaitingDianValidation(limit), this::checkValidation, "checked with the DIAN");
    }

    private int each(List<UUID> pending, Function<UUID, ElectronicDocument> step, String done) {
        int handled = 0;
        for (UUID documentUuid : pending) {
            try {
                step.apply(documentUuid);
                handled++;
            } catch (BillingException.DianUnavailable unavailable) {
                log.warn("The DIAN is unavailable; {} of {} electronic documents {}", handled, pending.size(), done);
                return handled;
            } catch (RuntimeException failed) {
                log.warn("Electronic document {} could not be {}: {}", documentUuid, done, failed.getMessage());
            }
        }
        if (!pending.isEmpty()) {
            log.info("{} of {} electronic documents {}", handled, pending.size(), done);
        }
        return handled;
    }

    private ElectronicDocument record(UUID documentUuid, DianVerdict.Operation operation, DianAnswer answer) {
        return transactions.execute(status -> {
            ElectronicDocument current = document(documentUuid);
            DianVerdict.Outcome outcome = switch (answer.verdict()) {
                case PROCESSING -> {
                    if (current.dianStatus() == null) {
                        current.awaitDianValidation(current.documentKey(), clock.instant());
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
            ElectronicDocument saved = documents.save(current);
            verdicts.save(DianVerdict.of(saved, operation, outcome, answer.statusCode(), answer.statusDescription(),
                    answer.errors(), clock.instant()));
            if (outcome == DianVerdict.Outcome.ACCEPTED && answer.applicationResponse() != null
                    && files.find(documentUuid, DocumentFile.Kind.DIAN_APPLICATION_RESPONSE).isEmpty()) {
                files.save(DocumentFile.of(saved, DocumentFile.Kind.DIAN_APPLICATION_RESPONSE,
                        answer.applicationResponse()));
            }
            if (outcome == DianVerdict.Outcome.ACCEPTED || outcome == DianVerdict.Outcome.REJECTED) {
                boolean invoice = saved.type() == ElectronicDocument.Type.INVOICE;
                if (invoice || outcome == DianVerdict.Outcome.ACCEPTED) {
                    events.invoiceChanged(saved.invoice().uuid(),
                            !invoice ? InvoiceEvents.Change.CreditNoteAcceptedByDian
                                    : outcome == DianVerdict.Outcome.ACCEPTED ? InvoiceEvents.Change.InvoiceAcceptedByDian
                                    : InvoiceEvents.Change.InvoiceRejectedByDian);
                }
            }
            log.info("{} {} {} by the DIAN ({} {})", saved.type(), saved.number(), outcome, answer.statusCode(),
                    answer.statusDescription());
            return saved;
        });
    }

    private ElectronicDocument document(UUID documentUuid) {
        return documents.findByUuid(documentUuid)
                .orElseThrow(() -> new IllegalStateException("Unknown electronic document " + documentUuid));
    }
}
