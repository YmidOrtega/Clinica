package com.ClinicaDeYmid.clinical_history_service.infrastructure.encryption;

import com.ClinicaDeYmid.clinical_history_service.support.TestEncryptionKeys;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MasterKeysTest {

    private static final UUID DATA_KEY = UUID.randomUUID();
    private static final UUID PATIENT = UUID.randomUUID();

    @Test
    void wrapsDataKeysBoundToTheirPatientAndMasterKey() {
        Path directory = TestEncryptionKeys.newDirectory();
        TestEncryptionKeys.write(directory, "master-2026");
        MasterKeys keys = MasterKeys.load(new EncryptionProperties(directory, "master-2026"));
        byte[] dataKey = AesGcm.randomKey();

        byte[] wrapped = keys.wrap(DATA_KEY, PATIENT, dataKey);

        assertThat(wrapped).hasSize(61);
        assertThat(keys.unwrap("master-2026", DATA_KEY, PATIENT, wrapped)).hasValueSatisfying(raw -> assertThat(raw).isEqualTo(dataKey));
        assertThat(keys.unwrap("master-2025", DATA_KEY, PATIENT, wrapped)).isEmpty();
        assertThatThrownBy(() -> keys.unwrap("master-2026", DATA_KEY, UUID.randomUUID(), wrapped))
                .isInstanceOf(EncryptedContentUnreadableException.class);
    }

    @Test
    void refusesToStartWithoutAValidActiveKey() throws Exception {
        Path directory = TestEncryptionKeys.newDirectory();
        Files.writeString(directory.resolve("short.key"), "c2hvcnQ=");

        assertThatThrownBy(() -> MasterKeys.load(new EncryptionProperties(directory, null))).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> MasterKeys.load(new EncryptionProperties(directory, "short"))).hasMessageContaining("32 random bytes");
        Files.delete(directory.resolve("short.key"));
        assertThatThrownBy(() -> MasterKeys.load(new EncryptionProperties(directory, "missing"))).hasMessageContaining("not in the keys directory");
    }
}
