package com.ClinicaDeYmid.billing_service.application;

import com.ClinicaDeYmid.billing_service.application.dian.InvoiceSigner;
import com.ClinicaDeYmid.billing_service.domain.BillingException;
import com.ClinicaDeYmid.billing_service.domain.Invoice;
import com.ClinicaDeYmid.billing_service.domain.InvoiceDocument;
import com.ClinicaDeYmid.billing_service.domain.InvoiceDocuments;
import com.ClinicaDeYmid.billing_service.domain.InvoiceStatus;
import com.ClinicaDeYmid.billing_service.domain.Invoices;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class InvoiceSigning {

    private static final Logger log = LoggerFactory.getLogger(InvoiceSigning.class);

    private final Invoices invoices;
    private final InvoiceDocuments documents;
    private final InvoiceSigner signer;
    private final TransactionOperations transactions;
    private final Clock clock;

    public InvoiceSigning(Invoices invoices, InvoiceDocuments documents, InvoiceSigner signer,
                          TransactionOperations transactions, Clock clock) {
        this.invoices = invoices;
        this.documents = documents;
        this.signer = signer;
        this.transactions = transactions;
        this.clock = clock;
    }

    public Invoice sign(UUID invoiceUuid) {
        Invoice invoice = invoices.findByUuid(invoiceUuid).orElseThrow(BillingException.InvoiceNotFound::new);
        if (invoice.signedAt() != null) {
            return invoice;
        }
        if (!(invoice.status() instanceof InvoiceStatus.Issued)) {
            throw new BillingException.InvoiceNotSignable();
        }
        String unsigned = documents.find(invoiceUuid, InvoiceDocument.Kind.UBL_UNSIGNED)
                .orElseThrow(() -> new IllegalStateException("Issued invoice " + invoiceUuid + " has no UBL"))
                .content();
        Instant at = clock.instant();
        String signed = signer.sign(unsigned, at);
        try {
            Invoice saved = transactions.execute(status -> {
                Invoice current = invoices.findByUuid(invoiceUuid).orElseThrow(BillingException.InvoiceNotFound::new);
                if (current.signedAt() != null) {
                    return current;
                }
                current.sign(at);
                Invoice updated = invoices.save(current);
                documents.save(InvoiceDocument.of(updated, InvoiceDocument.Kind.UBL_SIGNED, signed));
                return updated;
            });
            log.info("Invoice {} signed with XAdES-EPES", saved.number());
            return saved;
        } catch (DataIntegrityViolationException | OptimisticLockingFailureException raced) {
            return invoices.findByUuid(invoiceUuid).orElseThrow(BillingException.InvoiceNotFound::new);
        }
    }

    public boolean trySign(UUID invoiceUuid) {
        try {
            sign(invoiceUuid);
            return true;
        } catch (RuntimeException failed) {
            log.warn("Invoice {} stays unsigned until the next retry: {}", invoiceUuid, failed.getMessage());
            return false;
        }
    }

    public int signPending(int limit) {
        List<UUID> pending = invoices.awaitingSignature(limit);
        int signed = 0;
        for (UUID invoiceUuid : pending) {
            if (!trySign(invoiceUuid)) {
                break;
            }
            signed++;
        }
        if (!pending.isEmpty()) {
            log.info("Signed {} of {} invoices awaiting their signature", signed, pending.size());
        }
        return signed;
    }
}
