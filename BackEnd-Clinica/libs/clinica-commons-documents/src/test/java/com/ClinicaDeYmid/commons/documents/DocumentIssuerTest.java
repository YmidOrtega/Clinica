package com.ClinicaDeYmid.commons.documents;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DocumentIssuerTest {

    private static final String PURPOSE = "clinica.admissions.receipt/v1";
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-21T15:00:00Z"), ZoneOffset.UTC);

    private final LocalSealSigner signer = new LocalSealSigner("seal-2026");
    private final DocumentSealer sealer =
            new TransitDocumentSealer(SealKeyRing.of(signer, Map.of()), PURPOSE);
    private final DocumentIssuer issuer = new DocumentIssuer(sealer, PURPOSE, CLOCK);

    @Test
    void issuesADocumentWithItsFingerprintAndSealAndVerifiesItBack() {
        byte[] document = "un comprobante".getBytes(StandardCharsets.UTF_8);

        SealedDocument issued = issuer.issue(UUID.randomUUID(), UUID.randomUUID(), "RECEPTIONIST", document);

        assertThat(issued.sha256()).isEqualTo(Documents.sha256(document)).hasSize(64);
        assertThat(issued.keyId()).isEqualTo("seal-2026");
        assertThat(issued.issuedAt()).isEqualTo(CLOCK.instant());
        assertThat(issuer.verify(issued, document).authentic()).isTrue();
    }

    @Test
    void noticesADocumentThatIsNotTheOneIssued() {
        SealedDocument issued = issuer.issue(UUID.randomUUID(), UUID.randomUUID(), "DOCTOR",
                "original".getBytes(StandardCharsets.UTF_8));

        DocumentVerification verification = issuer.verify(issued, "alterado".getBytes(StandardCharsets.UTF_8));

        assertThat(verification.documentMatches()).isFalse();
        assertThat(verification.sealValid()).isTrue();
        assertThat(verification.authentic()).isFalse();
    }

    @Test
    void refusesASealMadeForAnotherPurposeOrWithAnUnknownKey() {
        byte[] document = "un comprobante".getBytes(StandardCharsets.UTF_8);
        String sha256 = Documents.sha256(document);
        DocumentSealer otherPurpose = new TransitDocumentSealer(SealKeyRing.of(signer, Map.of()),
                "clinica.clinical.record-copy/v1");

        assertThat(sealer.verify(sha256, otherPurpose.seal(sha256))).isFalse();
        assertThat(sealer.verify(sha256, new DocumentSeal("seal-2030", "AAAA"))).isFalse();
    }

    @Test
    void demandsAFingerprintAndAPurposeThatLookLikeOne() {
        assertThatThrownBy(() -> Documents.requireSha256("no")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Documents.requirePurpose("comprobante"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
