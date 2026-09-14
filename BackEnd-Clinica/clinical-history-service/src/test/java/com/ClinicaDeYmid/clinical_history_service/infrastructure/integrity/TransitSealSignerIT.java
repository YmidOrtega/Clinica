package com.ClinicaDeYmid.clinical_history_service.infrastructure.integrity;

import com.ClinicaDeYmid.clinical_history_service.domain.integrity.ChainLink;
import com.ClinicaDeYmid.clinical_history_service.domain.integrity.ChainVerification;
import com.ClinicaDeYmid.clinical_history_service.domain.integrity.IntegrityProblem;
import com.ClinicaDeYmid.clinical_history_service.domain.integrity.LedgerEntry;
import com.ClinicaDeYmid.clinical_history_service.domain.copy.DocumentSealer.DocumentSeal;
import com.ClinicaDeYmid.clinical_history_service.infrastructure.transit.TransitClient;
import com.ClinicaDeYmid.clinical_history_service.infrastructure.transit.TransitKeys;
import com.ClinicaDeYmid.clinical_history_service.support.OpenBaoTestContainer;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.ClinicaDeYmid.clinical_history_service.infrastructure.integrity.SampleLedgerEntries.AT;
import static com.ClinicaDeYmid.clinical_history_service.infrastructure.integrity.SampleLedgerEntries.PATIENT;
import static com.ClinicaDeYmid.clinical_history_service.infrastructure.integrity.SampleLedgerEntries.everyKindOfEntry;
import static com.ClinicaDeYmid.clinical_history_service.infrastructure.integrity.SampleLedgerEntries.seal;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TransitSealSignerIT {

    private static final TransitClient TRANSIT = new TransitClient(OpenBaoTestContainer.template(), "transit");

    @Test
    void sealsTheChainAndCopiesWithAKeyThatNeverLeavesOpenBao() {
        String key = OpenBaoTestContainer.createKey("seal", "ecdsa-p256");
        EcdsaClinicalSignature signature = signature(key, Map.of());
        List<LedgerEntry> entries = everyKindOfEntry();

        List<ChainLink> links = seal(signature, entries);
        DocumentSeal copySeal = signature.sealDocument("ab".repeat(32));

        assertThat(links).extracting(ChainLink::keyId).containsOnly(key + "-v1");
        assertThat(ChainVerification.of(PATIENT, links, entries, signature).verified()).isTrue();
        assertThat(signature.verifyDocument("ab".repeat(32), copySeal)).isTrue();
        assertThat(signature.verifyDocument("cd".repeat(32), copySeal)).isFalse();
        assertThat(signature.publicKeys()).containsOnlyKeys(key + "-v1");
    }

    @Test
    void aRotatedKeyKeepsEarlierSealsVerifiableAlongsideRetiredFileKeys() {
        String key = OpenBaoTestContainer.createKey("seal", "ecdsa-p256");
        LocalSealSigner legacy = new LocalSealSigner("seal-dev");
        EcdsaClinicalSignature legacySignature = new EcdsaClinicalSignature(SealKeyRing.of(legacy, Map.of()));
        List<LedgerEntry> entries = everyKindOfEntry();
        List<ChainLink> links = new ArrayList<>(seal(legacySignature, entries.subList(0, 1)));
        EcdsaClinicalSignature signature = signature(key, Map.of("seal-dev", legacy.publicPem("seal-dev")));
        links.add(signature.seal(entries.get(1), links.get(0), AT));

        OpenBaoTestContainer.rotate(key);
        EcdsaClinicalSignature rotated = signature(key, Map.of("seal-dev", legacy.publicPem("seal-dev")));
        links.add(rotated.seal(entries.get(2), links.get(1), AT));

        assertThat(links).extracting(ChainLink::keyId).containsExactly("seal-dev", key + "-v1", key + "-v2");
        assertThat(ChainVerification.of(PATIENT, links, entries.subList(0, 3), rotated).verified()).isTrue();
        assertThat(signature.check(entries.get(2), links.get(2))).isEmpty();
        assertThat(rotated.publicKeys()).containsOnlyKeys("seal-dev", key + "-v1", key + "-v2");
        assertThat(signature(key, Map.of()).check(entries.get(0), links.get(0))).containsExactly(IntegrityProblem.Kind.UNKNOWN_KEY);
    }

    @Test
    void refusesATransitKeyThatIsNotAnEcdsaP256Key() {
        String key = OpenBaoTestContainer.createKey("not-a-seal", "aes256-gcm96");

        assertThatThrownBy(() -> signature(key, Map.of())).hasMessageContaining("ecdsa-p256");
    }

    private static EcdsaClinicalSignature signature(String key, Map<String, String> retiredPublicKeys) {
        TransitKeys keys = new TransitKeys(TRANSIT, key, Duration.ofMinutes(5), Clock.systemUTC());
        return new EcdsaClinicalSignature(SealKeyRing.of(new TransitSealSigner(keys), retiredPublicKeys));
    }
}
