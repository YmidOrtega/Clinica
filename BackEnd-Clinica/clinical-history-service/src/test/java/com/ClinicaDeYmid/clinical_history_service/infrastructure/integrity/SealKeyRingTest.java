package com.ClinicaDeYmid.clinical_history_service.infrastructure.integrity;

import com.ClinicaDeYmid.clinical_history_service.support.TestSealKeys;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SealKeyRingTest {

    @Test
    void loadsTheActiveKeyAndEveryPublicKeyForVerification() {
        Path directory = TestSealKeys.newDirectory();
        TestSealKeys.write(directory, "seal-2025", false);
        TestSealKeys.write(directory, "seal-2026", true);

        SealKeyRing ring = SealKeyRing.load(new SealProperties(directory, "seal-2026"));

        assertThat(ring.activeKeyId()).isEqualTo("seal-2026");
        assertThat(ring.publicKeysPem()).containsOnlyKeys("seal-2025", "seal-2026");
        assertThat(ring.publicKeysPem().get("seal-2026")).startsWith("-----BEGIN PUBLIC KEY-----");
        byte[] data = "entry".getBytes(StandardCharsets.US_ASCII);
        assertThat(ring.verify("seal-2026", data, ring.sign(data))).contains(true);
        assertThat(ring.verify("seal-2025", data, ring.sign(data))).contains(false);
        assertThat(ring.verify("seal-2024", data, ring.sign(data))).isEmpty();
    }

    @Test
    void refusesToStartWithoutAUsableActiveKey() {
        Path directory = TestSealKeys.newDirectory();
        TestSealKeys.write(directory, "public-only", false);

        assertThatThrownBy(() -> SealKeyRing.load(new SealProperties(directory, null))).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> SealKeyRing.load(new SealProperties(directory, "public-only")))
                .hasMessageContaining("private and public");
        assertThatThrownBy(() -> SealKeyRing.load(new SealProperties(directory, "../etc/passwd")))
                .hasMessageContaining("invalid characters");
    }

    @Test
    void refusesMismatchedPairsAndOtherCurves() throws Exception {
        Path mismatched = TestSealKeys.newDirectory();
        TestSealKeys.write(mismatched, "a", true);
        TestSealKeys.write(mismatched, "b", true);
        Files.copy(mismatched.resolve("b.public.pem"), mismatched.resolve("a.public.pem"), StandardCopyOption.REPLACE_EXISTING);
        Path otherCurve = TestSealKeys.newDirectory();
        TestSealKeys.write(otherCurve, "p384", true, "secp384r1");

        assertThatThrownBy(() -> SealKeyRing.load(new SealProperties(mismatched, "a"))).hasMessageContaining("do not match");
        assertThatThrownBy(() -> SealKeyRing.load(new SealProperties(otherCurve, "p384"))).hasMessageContaining("P-256");
    }

    @Test
    void treatsMalformedSealsAsInvalid() {
        Path directory = TestSealKeys.newDirectory();
        TestSealKeys.write(directory, "k", true);
        SealKeyRing ring = SealKeyRing.load(new SealProperties(directory, "k"));

        assertThat(ring.verify("k", new byte[]{1}, Base64.getDecoder().decode("AAAA"))).contains(false);
    }
}
