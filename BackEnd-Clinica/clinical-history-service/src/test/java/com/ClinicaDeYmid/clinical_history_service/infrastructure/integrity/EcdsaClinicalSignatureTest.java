package com.ClinicaDeYmid.clinical_history_service.infrastructure.integrity;

import com.ClinicaDeYmid.clinical_history_service.domain.integrity.ChainLink;
import com.ClinicaDeYmid.clinical_history_service.domain.integrity.ChainVerification;
import com.ClinicaDeYmid.clinical_history_service.domain.integrity.IntegrityProblem;
import com.ClinicaDeYmid.clinical_history_service.domain.integrity.LedgerEntry;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.ClinicaDeYmid.clinical_history_service.infrastructure.integrity.SampleLedgerEntries.AT;
import static com.ClinicaDeYmid.clinical_history_service.infrastructure.integrity.SampleLedgerEntries.PATIENT;
import static com.ClinicaDeYmid.clinical_history_service.infrastructure.integrity.SampleLedgerEntries.everyKindOfEntry;
import static com.ClinicaDeYmid.clinical_history_service.infrastructure.integrity.SampleLedgerEntries.seal;
import static com.ClinicaDeYmid.clinical_history_service.infrastructure.integrity.SampleLedgerEntries.triageNote;
import static org.assertj.core.api.Assertions.assertThat;

class EcdsaClinicalSignatureTest {

    private final LocalSealSigner signer = new LocalSealSigner("seal-2026");
    private final EcdsaClinicalSignature signature = new EcdsaClinicalSignature(SealKeyRing.of(signer, Map.of()));

    @Test
    void theCanonicalFormOfANoteIsStable() {
        String canonical = new String(CanonicalPayloads.payload(triageNote("Dolor torácico")), StandardCharsets.UTF_8);

        assertThat(canonical).isEqualTo("{\"author\":{\"email\":\"nurse@clinica.test\",\"role\":\"NURSE\","
                + "\"uuid\":\"00000000-0000-4000-8000-000000000004\"},\"content\":{\"level\":\"II\",\"reason\":\"Dolor torácico\","
                + "\"type\":\"TRIAGE\"},\"encounterId\":\"8a1d2c3b-4e5f-4a6b-8c7d-9e0f1a2b3c4d\",\"entryType\":\"NOTE_SIGNED\","
                + "\"extemporaneous\":false,\"id\":\"5b6c7d8e-9f0a-4b1c-8d2e-3f4a5b6c7d8e\",\"occurredAt\":\"2026-09-10T14:00:00.123456Z\","
                + "\"patientUuid\":\"3f6c1b2a-7d4e-4a5b-9c8d-1e2f3a4b5c6d\",\"recordedAt\":\"2026-09-10T14:05:00.123456Z\","
                + "\"type\":\"TRIAGE\"}");
        assertThat(CanonicalPayloads.sha256(canonical.getBytes(StandardCharsets.UTF_8)))
                .isEqualTo(CanonicalPayloads.sha256(CanonicalPayloads.payload(triageNote("Dolor torácico"))));
    }

    @Test
    void sealsEveryKindOfEntryIntoAVerifiableChain() {
        List<LedgerEntry> entries = everyKindOfEntry();
        List<ChainLink> links = seal(signature, entries);

        assertThat(links).extracting(ChainLink::sequence).containsExactly(1L, 2L, 3L, 4L);
        assertThat(links.get(0).previousHash()).isEqualTo(ChainLink.GENESIS_HASH);
        assertThat(links.get(1).previousHash()).isEqualTo(links.get(0).entryHash());
        assertThat(ChainVerification.of(PATIENT, links, entries, signature).verified()).isTrue();
    }

    @Test
    void detectsChangedClinicalContent() {
        ChainLink link = signature.seal(triageNote("Dolor torácico"), null, AT);

        assertThat(signature.check(triageNote("Dolor leve"), link)).containsExactly(IntegrityProblem.Kind.PAYLOAD_MISMATCH);
    }

    @Test
    void recomputingTheHashesWithoutThePrivateKeyInvalidatesTheSeal() {
        LedgerEntry forged = triageNote("Dolor leve");
        ChainLink original = signature.seal(triageNote("Dolor torácico"), null, AT);
        String payloadHash = CanonicalPayloads.sha256(CanonicalPayloads.payload(forged));
        String entryHash = CanonicalPayloads.sha256(CanonicalPayloads.entry(PATIENT, 1, original.entryType(), original.entryId(), 1,
                payloadHash, original.previousHash(), original.keyId(), original.sealedAt()));
        ChainLink rewritten = new ChainLink(PATIENT, 1, original.entryType(), original.entryId(), 1, payloadHash, original.previousHash(),
                entryHash, original.keyId(), original.seal(), original.sealedAt());
        ChainLink editedHeader = new ChainLink(PATIENT, 1, original.entryType(), original.entryId(), 1, original.payloadHash(),
                original.previousHash(), original.entryHash(), original.keyId(), original.seal(), AT.plusSeconds(1));

        assertThat(signature.check(forged, rewritten)).containsExactly(IntegrityProblem.Kind.INVALID_SEAL);
        assertThat(signature.check(triageNote("Dolor torácico"), editedHeader)).containsExactly(IntegrityProblem.Kind.ENTRY_HASH_MISMATCH);
    }

    @Test
    void entriesSealedWithARetiredKeyStillVerifyAfterRotation() {
        List<LedgerEntry> entries = everyKindOfEntry();
        List<ChainLink> sealedIn2026 = seal(signature, entries.subList(0, 2));
        EcdsaClinicalSignature rotated = new EcdsaClinicalSignature(
                SealKeyRing.of(new LocalSealSigner("seal-2027"), Map.of("seal-2026", signer.publicPem("seal-2026"))));
        List<ChainLink> links = new ArrayList<>(sealedIn2026);
        links.add(rotated.seal(entries.get(2), links.get(1), AT));

        assertThat(links.get(2).keyId()).isEqualTo("seal-2027");
        assertThat(ChainVerification.of(PATIENT, links, entries.subList(0, 3), rotated).verified()).isTrue();

        EcdsaClinicalSignature withoutOldKey = new EcdsaClinicalSignature(SealKeyRing.of(new LocalSealSigner("seal-2027"), Map.of()));
        assertThat(withoutOldKey.check(entries.get(0), links.get(0))).containsExactly(IntegrityProblem.Kind.UNKNOWN_KEY);
    }
}
