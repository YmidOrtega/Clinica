package com.ClinicaDeYmid.billing_service.application;

import com.ClinicaDeYmid.billing_service.application.dian.DocumentSigner;
import com.ClinicaDeYmid.billing_service.application.dian.ElectronicAttachment;
import com.ClinicaDeYmid.billing_service.application.dian.UblWriter;
import com.ClinicaDeYmid.billing_service.domain.BillingException;
import com.ClinicaDeYmid.billing_service.domain.DianStatus;
import com.ClinicaDeYmid.billing_service.domain.DocumentFile;
import com.ClinicaDeYmid.billing_service.domain.DocumentFiles;
import com.ClinicaDeYmid.billing_service.domain.ElectronicDocument;
import com.ClinicaDeYmid.billing_service.domain.ElectronicDocuments;
import com.ClinicaDeYmid.billing_service.domain.Issuers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class DocumentAttachment {

    private static final Logger log = LoggerFactory.getLogger(DocumentAttachment.class);

    private final ElectronicDocuments documents;
    private final DocumentFiles files;
    private final Issuers issuers;
    private final UblWriter ubl;
    private final DocumentSigner signer;
    private final TransactionOperations transactions;
    private final Clock clock;

    public DocumentAttachment(ElectronicDocuments documents, DocumentFiles files, Issuers issuers, UblWriter ubl,
                              DocumentSigner signer, TransactionOperations transactions, Clock clock) {
        this.documents = documents;
        this.files = files;
        this.issuers = issuers;
        this.ubl = ubl;
        this.signer = signer;
        this.transactions = transactions;
        this.clock = clock;
    }

    public DocumentFile attach(UUID documentUuid) {
        Instant at = clock.instant();
        ElectronicAttachment attachment = transactions.execute(status -> {
            ElectronicDocument document = documents.findByUuid(documentUuid)
                    .orElseThrow(() -> new IllegalStateException("Unknown electronic document " + documentUuid));
            if (document.dianStatus() != DianStatus.ACCEPTED) {
                throw new BillingException.AttachedDocumentNotReady();
            }
            String signed = files.find(documentUuid, DocumentFile.Kind.UBL_SIGNED)
                    .orElseThrow(BillingException.AttachedDocumentNotReady::new).content();
            String response = files.find(documentUuid, DocumentFile.Kind.DIAN_APPLICATION_RESPONSE)
                    .orElseThrow(BillingException.AttachedDocumentNotReady::new).content();
            return ElectronicAttachment.of(document,
                    issuers.find().orElseThrow(BillingException.IssuerNotConfigured::new), signed, response, at);
        });
        DocumentFile existing = files.find(documentUuid, DocumentFile.Kind.ATTACHED_DOCUMENT).orElse(null);
        if (existing != null) {
            return existing;
        }
        String container = signer.sign(ubl.attachedDocument(attachment), at);
        try {
            DocumentFile saved = transactions.execute(status -> files.save(DocumentFile.of(
                    documents.findByUuid(documentUuid).orElseThrow(), DocumentFile.Kind.ATTACHED_DOCUMENT, container)));
            log.info("{} {} wrapped in its signed AttachedDocument", attachment.type(), attachment.number());
            return saved;
        } catch (DataIntegrityViolationException raced) {
            return files.find(documentUuid, DocumentFile.Kind.ATTACHED_DOCUMENT).orElseThrow(() -> raced);
        }
    }

    public int attachPending(int limit) {
        List<UUID> pending = documents.awaitingAttachment(limit);
        int attached = 0;
        for (UUID documentUuid : pending) {
            try {
                attach(documentUuid);
                attached++;
            } catch (RuntimeException failed) {
                log.warn("Electronic document {} stays without its AttachedDocument until the next retry: {}",
                        documentUuid, failed.getMessage());
                break;
            }
        }
        return attached;
    }
}
