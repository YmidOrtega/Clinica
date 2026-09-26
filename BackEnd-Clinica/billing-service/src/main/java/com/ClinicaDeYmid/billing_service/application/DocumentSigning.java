package com.ClinicaDeYmid.billing_service.application;

import com.ClinicaDeYmid.billing_service.application.dian.DocumentSigner;
import com.ClinicaDeYmid.billing_service.domain.BillingException;
import com.ClinicaDeYmid.billing_service.domain.DocumentFile;
import com.ClinicaDeYmid.billing_service.domain.DocumentFiles;
import com.ClinicaDeYmid.billing_service.domain.ElectronicDocument;
import com.ClinicaDeYmid.billing_service.domain.ElectronicDocuments;
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
public class DocumentSigning {

    private static final Logger log = LoggerFactory.getLogger(DocumentSigning.class);

    private final ElectronicDocuments documents;
    private final DocumentFiles files;
    private final DocumentSigner signer;
    private final TransactionOperations transactions;
    private final Clock clock;

    public DocumentSigning(ElectronicDocuments documents, DocumentFiles files, DocumentSigner signer,
                           TransactionOperations transactions, Clock clock) {
        this.documents = documents;
        this.files = files;
        this.signer = signer;
        this.transactions = transactions;
        this.clock = clock;
    }

    public ElectronicDocument sign(UUID documentUuid) {
        ElectronicDocument document = document(documentUuid);
        if (document.signedAt() != null) {
            return document;
        }
        String unsigned = files.find(documentUuid, DocumentFile.Kind.UBL_UNSIGNED)
                .orElseThrow(() -> new IllegalStateException("Electronic document " + documentUuid + " has no UBL"))
                .content();
        Instant at = clock.instant();
        String signed = signer.sign(unsigned, at);
        try {
            ElectronicDocument saved = transactions.execute(status -> {
                ElectronicDocument current = document(documentUuid);
                if (current.signedAt() != null) {
                    return current;
                }
                current.sign(at);
                ElectronicDocument updated = documents.save(current);
                files.save(DocumentFile.of(updated, DocumentFile.Kind.UBL_SIGNED, signed));
                return updated;
            });
            log.info("{} {} signed with XAdES-EPES", saved.type(), saved.number());
            return saved;
        } catch (DataIntegrityViolationException | OptimisticLockingFailureException raced) {
            return document(documentUuid);
        }
    }

    public boolean trySign(UUID documentUuid) {
        try {
            sign(documentUuid);
            return true;
        } catch (RuntimeException failed) {
            log.warn("Electronic document {} stays unsigned until the next retry: {}", documentUuid,
                    failed.getMessage());
            return false;
        }
    }

    public int signPending(int limit) {
        List<UUID> pending = documents.awaitingSignature(limit);
        int signed = 0;
        for (UUID documentUuid : pending) {
            if (!trySign(documentUuid)) {
                break;
            }
            signed++;
        }
        if (!pending.isEmpty()) {
            log.info("Signed {} of {} electronic documents awaiting their signature", signed, pending.size());
        }
        return signed;
    }

    private ElectronicDocument document(UUID documentUuid) {
        return documents.findByUuid(documentUuid)
                .orElseThrow(() -> new IllegalStateException("Unknown electronic document " + documentUuid));
    }
}
