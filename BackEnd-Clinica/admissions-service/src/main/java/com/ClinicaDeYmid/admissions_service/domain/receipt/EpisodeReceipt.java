package com.ClinicaDeYmid.admissions_service.domain.receipt;

import com.ClinicaDeYmid.commons.documents.SealedDocument;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record EpisodeReceipt(SealedDocument document, String admissionNumber) {

    public static final String PURPOSE = "clinica.admissions.receipt/v1";

    public EpisodeReceipt {
        Objects.requireNonNull(document, "document");
        Objects.requireNonNull(admissionNumber, "admissionNumber");
    }

    public UUID id() {
        return document.id();
    }

    public UUID admissionUuid() {
        return document.subjectId();
    }

    public UUID issuedBy() {
        return document.issuedBy();
    }

    public String issuedByRole() {
        return document.issuedByRole();
    }

    public Instant issuedAt() {
        return document.issuedAt();
    }

    public String sha256() {
        return document.sha256();
    }

    public String keyId() {
        return document.keyId();
    }

    public String seal() {
        return document.seal().value();
    }
}
