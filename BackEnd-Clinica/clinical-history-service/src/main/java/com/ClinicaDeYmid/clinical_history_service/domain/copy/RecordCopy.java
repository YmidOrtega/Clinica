package com.ClinicaDeYmid.clinical_history_service.domain.copy;

import com.ClinicaDeYmid.commons.documents.SealedDocument;
import com.ClinicaDeYmid.clinical_history_service.domain.ClinicalException;
import com.ClinicaDeYmid.clinical_history_service.domain.ClinicalText;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record RecordCopy(
        SealedDocument document,
        String reason,
        Instant periodFrom,
        Instant periodTo,
        int entries,
        boolean chainVerified) {

    public static final String PURPOSE = "clinica.clinical.record-copy/v1";

    public RecordCopy {
        Objects.requireNonNull(document, "document");
        Objects.requireNonNull(reason, "reason");
    }

    public UUID id() {
        return document.id();
    }

    public UUID patientUuid() {
        return document.subjectId();
    }

    public UUID requestedBy() {
        return document.issuedBy();
    }

    public String requestedRole() {
        return document.issuedByRole();
    }

    public String documentSha256() {
        return document.sha256();
    }

    public String keyId() {
        return document.keyId();
    }

    public String seal() {
        return document.seal().value();
    }

    public Instant generatedAt() {
        return document.issuedAt();
    }

    public static String requireReason(String reason) {
        String text = ClinicalText.required(reason, "reason", ClinicalText.SHORT);
        if (text.length() < 10) {
            throw new ClinicalException.InvalidData("reason", "debe explicar la solicitud en al menos 10 caracteres");
        }
        return text;
    }

    public static void requirePeriod(Instant from, Instant to) {
        if (from != null && to != null && !from.isBefore(to)) {
            throw new ClinicalException.InvalidData("from", "debe ser anterior a 'to'");
        }
    }
}
