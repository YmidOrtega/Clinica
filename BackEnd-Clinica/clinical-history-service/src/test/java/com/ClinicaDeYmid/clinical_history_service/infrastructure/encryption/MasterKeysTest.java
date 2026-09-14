package com.ClinicaDeYmid.clinical_history_service.infrastructure.encryption;

import com.ClinicaDeYmid.clinical_history_service.support.TestEncryptionKeys;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MasterKeysTest {

    private static final UUID DATA_KEY = UUID.randomUUID();
    private static final UUID PATIENT = UUID.randomUUID();
    private static final Map<String, String> NO_RETIRED_KEYS = Map.of();

    @Test
    void wrapsDataKeysBoundToTheirPatientAndMasterKey() {
        MasterKeys keys = MasterKeys.of(LocalKeyEncryptionKeys.active("master-2026", Map.of("master-2026", TestEncryptionKeys.randomBase64())),
                LocalKeyEncryptionKeys.retired(NO_RETIRED_KEYS));
        byte[] dataKey = AesGcm.randomKey();

        MasterKeys.Wrapping wrapping = keys.wrap(DATA_KEY, PATIENT, dataKey);

        assertThat(wrapping.masterKeyId()).isEqualTo("master-2026");
        assertThat(wrapping.wrappedKey()).hasSize(61);
        assertThat(keys.unwrap("master-2026", DATA_KEY, PATIENT, wrapping.wrappedKey())).hasValueSatisfying(raw -> assertThat(raw).isEqualTo(dataKey));
        assertThat(keys.unwrap("master-2025", DATA_KEY, PATIENT, wrapping.wrappedKey())).isEmpty();
        assertThatThrownBy(() -> keys.unwrap("master-2026", DATA_KEY, UUID.randomUUID(), wrapping.wrappedKey()))
                .isInstanceOf(EncryptedContentUnreadableException.class);
    }

    @Test
    void retiredKeysOnlyOpenWhatTheyWrappedBefore() {
        String retiredKey = TestEncryptionKeys.randomBase64();
        byte[] dataKey = AesGcm.randomKey();
        byte[] wrappedBefore = MasterKeys.of(LocalKeyEncryptionKeys.active("master-dev", Map.of("master-dev", retiredKey)),
                LocalKeyEncryptionKeys.retired(NO_RETIRED_KEYS)).wrap(DATA_KEY, PATIENT, dataKey).wrappedKey();

        MasterKeys keys = MasterKeys.of(LocalKeyEncryptionKeys.active("master-2026", Map.of("master-2026", TestEncryptionKeys.randomBase64())),
                LocalKeyEncryptionKeys.retired(Map.of("master-dev", retiredKey)));

        assertThat(keys.activeKeyId()).isEqualTo("master-2026");
        assertThat(keys.availableKeyIds()).containsExactly("master-2026", "master-dev");
        assertThat(keys.unwrap("master-dev", DATA_KEY, PATIENT, wrappedBefore)).hasValueSatisfying(raw -> assertThat(raw).isEqualTo(dataKey));
        assertThat(keys.wrap(DATA_KEY, PATIENT, dataKey).masterKeyId()).isEqualTo("master-2026");
        assertThatThrownBy(() -> LocalKeyEncryptionKeys.retired(Map.of("master-dev", retiredKey)).wrap("master-dev", dataKey, new byte[0]))
                .hasMessageContaining("retired");
    }

    @Test
    void refusesInvalidKeysAndAMissingActiveKey() {
        assertThatThrownBy(() -> LocalKeyEncryptionKeys.retired(Map.of("short", "c2hvcnQ="))).hasMessageContaining("32 random bytes");
        assertThatThrownBy(() -> LocalKeyEncryptionKeys.retired(Map.of("broken", "***"))).hasMessageContaining("not valid Base64");
        assertThatThrownBy(() -> LocalKeyEncryptionKeys.retired(Map.of("../etc", TestEncryptionKeys.randomBase64())))
                .hasMessageContaining("invalid characters");
        assertThatThrownBy(() -> LocalKeyEncryptionKeys.active("missing", Map.of("other", TestEncryptionKeys.randomBase64())))
                .hasMessageContaining("not among");
        assertThatThrownBy(() -> MasterKeys.of(LocalKeyEncryptionKeys.retired(NO_RETIRED_KEYS), LocalKeyEncryptionKeys.retired(NO_RETIRED_KEYS)))
                .hasMessageContaining("active master key");
    }
}
