package com.ClinicaDeYmid.clinical_history_service.infrastructure.integrity;

import com.ClinicaDeYmid.clinical_history_service.support.TestSealKeys;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SealKeyRingTest {

    @Test
    void signsWithTheActiveKeyAndVerifiesWithRetiredPublicKeys() {
        LocalSealSigner signer = new LocalSealSigner("clinical-seal-v1");
        String retired = TestSealKeys.publicPem(TestSealKeys.generate().getPublic());

        SealKeyRing ring = SealKeyRing.of(signer, Map.of("seal-2025", retired));

        byte[] data = "entry".getBytes(StandardCharsets.US_ASCII);
        byte[] seal = ring.sign(ring.activeKeyId(), data);
        assertThat(ring.activeKeyId()).isEqualTo("clinical-seal-v1");
        assertThat(ring.publicKeysPem()).containsOnlyKeys("clinical-seal-v1", "seal-2025");
        assertThat(ring.publicKeysPem().get("seal-2025")).startsWith("-----BEGIN PUBLIC KEY-----");
        assertThat(ring.verify("clinical-seal-v1", data, seal)).contains(true);
        assertThat(ring.verify("seal-2025", data, seal)).contains(false);
        assertThat(ring.verify("seal-2024", data, seal)).isEmpty();
        assertThat(ring.knows("seal-2025")).isTrue();
    }

    @Test
    void refusesRetiredKeysThatCannotVerifySeals() {
        LocalSealSigner signer = new LocalSealSigner("clinical-seal-v1");
        String p384 = TestSealKeys.publicPem(TestSealKeys.generate("secp384r1").getPublic());
        String valid = TestSealKeys.publicPem(TestSealKeys.generate().getPublic());

        assertThatThrownBy(() -> SealKeyRing.of(signer, Map.of("p384", p384))).hasMessageContaining("P-256");
        assertThatThrownBy(() -> SealKeyRing.of(signer, Map.of("garbage", "-----BEGIN PUBLIC KEY-----\nAAAA\n-----END PUBLIC KEY-----")))
                .hasMessageContaining("Invalid EC public key");
        assertThatThrownBy(() -> SealKeyRing.of(signer, Map.of("../etc/passwd", valid))).hasMessageContaining("invalid characters");
        assertThatThrownBy(() -> SealKeyRing.of(signer, Map.of("clinical-seal-v1", valid))).hasMessageContaining("collides");
    }

    @Test
    void treatsMalformedSealsAsInvalid() {
        SealKeyRing ring = SealKeyRing.of(new LocalSealSigner("k"), Map.of());

        assertThat(ring.verify("k", new byte[]{1}, Base64.getDecoder().decode("AAAA"))).contains(false);
    }
}
