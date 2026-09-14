package com.ClinicaDeYmid.clinical_history_service.infrastructure.encryption;

import com.ClinicaDeYmid.clinical_history_service.infrastructure.config.ClockConfiguration;
import com.ClinicaDeYmid.clinical_history_service.infrastructure.encryption.ContentEncryption.EncryptedField;
import com.ClinicaDeYmid.clinical_history_service.infrastructure.encryption.ContentEncryption.Purpose;
import com.ClinicaDeYmid.clinical_history_service.support.MySqlTestContainer;
import com.ClinicaDeYmid.clinical_history_service.support.TestEncryptionKeys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({DataKeyStore.class, ClockConfiguration.class, MySqlTestContainer.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ContentEncryptionIT {

    private static final byte[] NOTE = "{\"type\":\"TRIAGE\",\"reason\":\"Ideación suicida\"}".getBytes(StandardCharsets.UTF_8);

    @Autowired
    private DataKeyStore store;

    @Autowired
    private TransactionTemplate transactions;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private Clock clock;

    private Path keys;

    @BeforeEach
    void freshKeys() {
        jdbc.update("SET FOREIGN_KEY_CHECKS = 0");
        jdbc.update("DELETE FROM clinical_keys.data_key_wrappings");
        jdbc.update("DELETE FROM clinical_keys.data_keys");
        jdbc.update("SET FOREIGN_KEY_CHECKS = 1");
        keys = TestEncryptionKeys.newDirectory();
        TestEncryptionKeys.write(keys, "master-2026");
    }

    @Test
    void encryptsWithOneDataKeyPerPatientAndFreshNonces() {
        ContentEncryption encryption = encryption("master-2026");
        UUID patient = UUID.randomUUID();
        UUID note = UUID.randomUUID();

        EncryptedField first = encryption.encrypt(patient, Purpose.NOTE_CONTENT, note, NOTE);
        EncryptedField second = encryption.encrypt(patient, Purpose.NOTE_CONTENT, note, NOTE);
        EncryptedField otherPatient = encryption.encrypt(UUID.randomUUID(), Purpose.NOTE_CONTENT, note, NOTE);

        assertThat(second.dataKeyId()).isEqualTo(first.dataKeyId());
        assertThat(otherPatient.dataKeyId()).isNotEqualTo(first.dataKeyId());
        assertThat(second.ciphertext()).isNotEqualTo(first.ciphertext());
        assertThat(new String(first.ciphertext(), StandardCharsets.ISO_8859_1)).doesNotContain("suicida");
        assertThat(encryption("master-2026").decrypt(first, Purpose.NOTE_CONTENT, note)).isEqualTo(NOTE);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM clinical_keys.data_keys", Long.class)).isEqualTo(2);
    }

    @Test
    void ciphertextOnlyOpensForTheRecordAndFieldItWasWrittenFor() {
        ContentEncryption encryption = encryption("master-2026");
        UUID patient = UUID.randomUUID();
        UUID note = UUID.randomUUID();
        EncryptedField field = encryption.encrypt(patient, Purpose.NOTE_CONTENT, note, NOTE);
        byte[] flipped = field.ciphertext().clone();
        flipped[flipped.length - 20] ^= 1;

        assertThatThrownBy(() -> encryption.decrypt(field, Purpose.NOTE_CONTENT, UUID.randomUUID()))
                .isInstanceOf(EncryptedContentUnreadableException.class);
        assertThatThrownBy(() -> encryption.decrypt(field, Purpose.DRAFT_CONTENT, note))
                .isInstanceOf(EncryptedContentUnreadableException.class);
        assertThatThrownBy(() -> encryption.decrypt(new EncryptedField(field.dataKeyId(), flipped), Purpose.NOTE_CONTENT, note))
                .isInstanceOf(EncryptedContentUnreadableException.class);
    }

    @Test
    void dataKeysCreatedInARolledBackTransactionAreNeverReused() {
        ContentEncryption encryption = encryption("master-2026");
        UUID patient = UUID.randomUUID();

        UUID discarded = transactions.execute(status -> {
            UUID id = encryption.encrypt(patient, Purpose.DRAFT_CONTENT, UUID.randomUUID(), NOTE).dataKeyId();
            status.setRollbackOnly();
            return id;
        });
        EncryptedField kept = encryption.encrypt(patient, Purpose.DRAFT_CONTENT, UUID.randomUUID(), NOTE);

        assertThat(kept.dataKeyId()).isNotEqualTo(discarded);
        assertThat(jdbc.queryForObject("SELECT id FROM clinical_keys.data_keys WHERE patient_uuid = ?", String.class, patient.toString()))
                .isEqualTo(kept.dataKeyId().toString());
    }

    @Test
    void rotatesTheMasterKeyWithoutReencryptingContent() throws Exception {
        UUID patient = UUID.randomUUID();
        UUID note = UUID.randomUUID();
        EncryptedField field = encryption("master-2026").encrypt(patient, Purpose.NOTE_CONTENT, note, NOTE);
        TestEncryptionKeys.write(keys, "master-2027");
        ContentEncryption rotated = encryption("master-2027");

        assertThat(rotated.status().pendingRewrap()).isEqualTo(1);
        assertThat(rotated.status().retiredKeysCanBeRemoved()).isFalse();
        assertThat(rotated.rewrapWithActiveMasterKey()).isEqualTo(1);
        assertThat(rotated.rewrapWithActiveMasterKey()).isZero();
        assertThat(rotated.status().wrappingsPerMasterKey()).containsEntry("master-2026", 1L).containsEntry("master-2027", 1L);
        assertThat(rotated.status().retiredKeysCanBeRemoved()).isTrue();

        Files.delete(keys.resolve("master-2026.key"));

        assertThat(encryption("master-2027").decrypt(field, Purpose.NOTE_CONTENT, note)).isEqualTo(NOTE);
    }

    @Test
    void contentIsRecoverableOnlyWhileABackupOfTheMasterKeyExists() throws Exception {
        UUID note = UUID.randomUUID();
        EncryptedField field = encryption("master-2026").encrypt(UUID.randomUUID(), Purpose.NOTE_CONTENT, note, NOTE);
        Path backup = TestEncryptionKeys.newDirectory();
        Files.copy(keys.resolve("master-2026.key"), backup.resolve("master-2026.key"));
        Files.delete(keys.resolve("master-2026.key"));
        TestEncryptionKeys.write(keys, "master-2027");

        assertThatThrownBy(() -> encryption("master-2027").decrypt(field, Purpose.NOTE_CONTENT, note))
                .isInstanceOf(EncryptedContentUnreadableException.class)
                .hasMessageContaining("No available master key");

        Files.copy(backup.resolve("master-2026.key"), keys.resolve("master-2026.key"), StandardCopyOption.REPLACE_EXISTING);

        assertThat(encryption("master-2027").decrypt(field, Purpose.NOTE_CONTENT, note)).isEqualTo(NOTE);
    }

    private ContentEncryption encryption(String activeKeyId) {
        return new ContentEncryption(MasterKeys.load(new EncryptionProperties(keys, activeKeyId)), store, transactions, clock);
    }
}
