package com.ClinicaDeYmid.commons.documents;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

public final class DocumentIssuer {

    private final DocumentSealer sealer;
    private final String purpose;
    private final Clock clock;

    public DocumentIssuer(DocumentSealer sealer, String purpose, Clock clock) {
        this.sealer = sealer;
        this.purpose = Documents.requirePurpose(purpose);
        this.clock = clock;
    }

    public SealedDocument issue(UUID subjectId, UUID issuedBy, String issuedByRole, byte[] document) {
        String sha256 = Documents.sha256(document);
        return new SealedDocument(UUID.randomUUID(), purpose, subjectId, issuedBy, issuedByRole,
                Instant.now(clock), sha256, sealer.seal(sha256));
    }

    public DocumentVerification verify(SealedDocument issued, byte[] document) {
        return verify(issued, Documents.sha256(document));
    }

    public DocumentVerification verify(SealedDocument issued, String sha256) {
        boolean matches = issued.sha256().equals(sha256);
        return new DocumentVerification(matches, sealer.verify(issued.sha256(), issued.seal()));
    }

    public String activeKeyId() {
        return sealer.activeKeyId();
    }
}
