package com.ClinicaDeYmid.admissions_service.application.receipt;

import com.ClinicaDeYmid.admissions_service.application.AuthorizationCommands;
import com.ClinicaDeYmid.admissions_service.application.patient.PatientDirectory;
import com.ClinicaDeYmid.admissions_service.domain.Admission;
import com.ClinicaDeYmid.admissions_service.domain.Admissions;
import com.ClinicaDeYmid.admissions_service.domain.AdmissionsException;
import com.ClinicaDeYmid.admissions_service.domain.receipt.EpisodeReceipt;
import com.ClinicaDeYmid.admissions_service.domain.receipt.EpisodeReceipts;
import com.ClinicaDeYmid.commons.documents.DocumentIssuer;
import com.ClinicaDeYmid.commons.documents.DocumentVerification;
import com.ClinicaDeYmid.commons.documents.Documents;
import com.ClinicaDeYmid.commons.documents.SealedDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

import java.util.UUID;

@Service
public class ReceiptService {

    private static final Logger log = LoggerFactory.getLogger(ReceiptService.class);

    public record Issuer(UUID uuid, String role, String name) {
    }

    public record Issued(EpisodeReceipt receipt, byte[] document) {
    }

    public record Verified(EpisodeReceipt receipt, DocumentVerification verification) {
    }

    private final Admissions admissions;
    private final EpisodeReceipts receipts;
    private final PatientDirectory patients;
    private final AuthorizationCommands authorizations;
    private final ReceiptRenderer renderer;
    private final DocumentIssuer issuer;
    private final TransactionOperations transactions;

    public ReceiptService(Admissions admissions, EpisodeReceipts receipts, PatientDirectory patients,
                          AuthorizationCommands authorizations, ReceiptRenderer renderer, DocumentIssuer issuer,
                          TransactionOperations transactions) {
        this.admissions = admissions;
        this.receipts = receipts;
        this.patients = patients;
        this.authorizations = authorizations;
        this.renderer = renderer;
        this.issuer = issuer;
        this.transactions = transactions;
    }

    public Issued issue(UUID admissionUuid, Issuer requester) {
        Admission admission = admissions.findByUuid(admissionUuid)
                .orElseThrow(AdmissionsException.AdmissionNotFound::new);
        ReceiptContent content = new ReceiptContent(admission, patients.require(admission.patientUuid()),
                authorizations.ofAdmission(admissionUuid), requester.name(), issuer.activeKeyId());
        byte[] document = renderer.render(content);
        SealedDocument sealed = issuer.issue(admissionUuid, requester.uuid(), requester.role(), document);
        EpisodeReceipt receipt = new EpisodeReceipt(sealed, admission.number());
        transactions.executeWithoutResult(status -> receipts.add(receipt));
        log.info("Receipt {} of episode {} issued by {} ({} bytes)", receipt.id(), admission.number(),
                requester.uuid(), document.length);
        return new Issued(receipt, document);
    }

    public EpisodeReceipt find(UUID receiptId) {
        return receipts.find(receiptId).orElseThrow(AdmissionsException.ReceiptNotFound::new);
    }

    public Verified verify(UUID receiptId, byte[] document) {
        EpisodeReceipt receipt = find(receiptId);
        return new Verified(receipt, issuer.verify(receipt.document(), document));
    }

    public boolean authentic(String admissionNumber, String sha256) {
        return receipts.findByNumberAndFingerprint(admissionNumber, Documents.requireSha256(sha256))
                .map(receipt -> issuer.verify(receipt.document(), sha256).authentic())
                .orElse(false);
    }
}
