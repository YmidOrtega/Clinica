package com.ClinicaDeYmid.billing_service.domain;

import com.ClinicaDeYmid.commons.documents.SealedDocument;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record GraphicRepresentation(SealedDocument document, String documentNumber) {

    public static final String PURPOSE = "clinica.billing.graphic-representation/v1";

    public GraphicRepresentation {
        Objects.requireNonNull(document, "document");
        Objects.requireNonNull(documentNumber, "documentNumber");
    }

    public UUID id() {
        return document.id();
    }

    public UUID electronicDocumentUuid() {
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
