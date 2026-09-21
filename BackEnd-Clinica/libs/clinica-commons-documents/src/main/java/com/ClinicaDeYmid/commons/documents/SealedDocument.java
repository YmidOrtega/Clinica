package com.ClinicaDeYmid.commons.documents;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record SealedDocument(
        UUID id,
        String purpose,
        UUID subjectId,
        UUID issuedBy,
        String issuedByRole,
        Instant issuedAt,
        String sha256,
        DocumentSeal seal) {

    public SealedDocument {
        Objects.requireNonNull(id, "id");
        purpose = Documents.requirePurpose(purpose);
        Objects.requireNonNull(subjectId, "subjectId");
        Objects.requireNonNull(issuedBy, "issuedBy");
        Objects.requireNonNull(issuedByRole, "issuedByRole");
        Objects.requireNonNull(issuedAt, "issuedAt");
        sha256 = Documents.requireSha256(sha256);
        Objects.requireNonNull(seal, "seal");
    }

    public String keyId() {
        return seal.keyId();
    }
}
