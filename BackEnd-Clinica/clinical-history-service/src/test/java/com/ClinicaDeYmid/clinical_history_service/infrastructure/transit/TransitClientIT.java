package com.ClinicaDeYmid.clinical_history_service.infrastructure.transit;

import com.ClinicaDeYmid.clinical_history_service.support.OpenBaoTestContainer;
import org.junit.jupiter.api.Test;
import org.springframework.vault.authentication.TokenAuthentication;
import org.springframework.vault.client.VaultEndpoint;
import org.springframework.vault.core.VaultTemplate;

import java.net.URI;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TransitClientIT {

    private static final byte[] DATA_KEY = "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] PATIENT_A = "patient-a".getBytes(StandardCharsets.US_ASCII);

    private final TransitClient transit = new TransitClient(OpenBaoTestContainer.template(), "transit");

    @Test
    void encryptionIsBoundToItsAssociatedDataAndKeyVersion() {
        String key = OpenBaoTestContainer.createKey("kek", "aes256-gcm96");
        KeyVersion v1 = new KeyVersion(key, 1);

        byte[] wrapped = transit.encrypt(v1, DATA_KEY, PATIENT_A);

        assertThat(new String(wrapped, StandardCharsets.US_ASCII)).startsWith("vault:v1:");
        assertThat(transit.decrypt(v1, wrapped, PATIENT_A)).isEqualTo(DATA_KEY);
        assertThatThrownBy(() -> transit.decrypt(v1, wrapped, "patient-b".getBytes(StandardCharsets.US_ASCII)))
                .isInstanceOf(TransitRejectedException.class);
        assertThatThrownBy(() -> transit.decrypt(new KeyVersion(key, 2), wrapped, PATIENT_A)).isInstanceOf(TransitRejectedException.class);
    }

    @Test
    void describesTheVersionsAndPublicKeysOfAKey() {
        String key = OpenBaoTestContainer.createKey("seal", "ecdsa-p256");
        OpenBaoTestContainer.rotate(key);

        TransitKey described = transit.key(key);

        assertThat(described.type()).isEqualTo("ecdsa-p256");
        assertThat(described.latest().id()).isEqualTo(key + "-v2");
        assertThat(described.usableVersions()).containsOnlyKeys(key + "-v1", key + "-v2");
        assertThat(described.publicKeysPem().get(1)).startsWith("-----BEGIN PUBLIC KEY-----");
    }

    @Test
    void anUnreachableOrMissingKeyServiceIsReportedAsUnavailable() {
        TransitClient unreachable = new TransitClient(new VaultTemplate(VaultEndpoint.from(URI.create("http://127.0.0.1:1")),
                new TokenAuthentication("root")), "transit");

        assertThatThrownBy(() -> unreachable.key("clinical-kek")).isInstanceOf(ClinicalKeysUnavailableException.class);
        assertThatThrownBy(() -> transit.key("does-not-exist")).isInstanceOf(ClinicalKeysUnavailableException.class);
    }

    @Test
    void parsesOnlyVersionedTransitKeyIds() {
        assertThat(KeyVersion.parse("clinical-seal-v12")).contains(new KeyVersion("clinical-seal", 12));
        assertThat(KeyVersion.parse("seal-dev")).isEmpty();
        assertThat(KeyVersion.parse("clinical-seal-v0")).isEmpty();
        assertThat(KeyVersion.parse("../x-v1")).isEmpty();
    }
}
