package com.ClinicaDeYmid.clinical_history_service.infrastructure.encryption;

import com.ClinicaDeYmid.clinical_history_service.infrastructure.config.ClockConfiguration;
import com.ClinicaDeYmid.clinical_history_service.infrastructure.encryption.ContentEncryption.EncryptedField;
import com.ClinicaDeYmid.clinical_history_service.infrastructure.encryption.ContentEncryption.Purpose;
import com.ClinicaDeYmid.clinical_history_service.support.MySqlTestContainer;
import com.ClinicaDeYmid.clinical_history_service.support.TestEncryptionKeys;
import com.ClinicaDeYmid.commons.openbao.testing.OpenBaoTestContainer;
import com.ClinicaDeYmid.commons.openbao.transit.TransitClient;
import com.ClinicaDeYmid.commons.openbao.transit.TransitKeys;
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
import java.time.Clock;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
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

    private final Map<String, String> keys = new HashMap<>();

    @BeforeEach
    void freshKeys() {
        jdbc.update("SET FOREIGN_KEY_CHECKS = 0");
        jdbc.update("DELETE FROM clinical_keys.data_key_wrappings");
        jdbc.update("DELETE FROM clinical_keys.data_keys");
        jdbc.update("SET FOREIGN_KEY_CHECKS = 1");
        keys.clear();
        keys.put("master-2026", TestEncryptionKeys.randomBase64());
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
    void rotatesTheMasterKeyWithoutReencryptingContent() {
        UUID patient = UUID.randomUUID();
        UUID note = UUID.randomUUID();
        EncryptedField field = encryption("master-2026").encrypt(patient, Purpose.NOTE_CONTENT, note, NOTE);
        keys.put("master-2027", TestEncryptionKeys.randomBase64());
        ContentEncryption rotated = encryption("master-2027");

        assertThat(rotated.status().pendingRewrap()).isEqualTo(1);
        assertThat(rotated.status().retiredKeysCanBeRemoved()).isFalse();
        assertThat(rotated.rewrapWithActiveMasterKey()).isEqualTo(1);
        assertThat(rotated.rewrapWithActiveMasterKey()).isZero();
        assertThat(rotated.status().wrappingsPerMasterKey()).containsEntry("master-2026", 1L).containsEntry("master-2027", 1L);
        assertThat(rotated.status().retiredKeysCanBeRemoved()).isTrue();

        keys.remove("master-2026");

        assertThat(encryption("master-2027").decrypt(field, Purpose.NOTE_CONTENT, note)).isEqualTo(NOTE);
    }

    @Test
    void contentIsRecoverableOnlyWhileABackupOfTheMasterKeyExists() {
        UUID note = UUID.randomUUID();
        EncryptedField field = encryption("master-2026").encrypt(UUID.randomUUID(), Purpose.NOTE_CONTENT, note, NOTE);
        String backup = keys.remove("master-2026");
        keys.put("master-2027", TestEncryptionKeys.randomBase64());

        assertThatThrownBy(() -> encryption("master-2027").decrypt(field, Purpose.NOTE_CONTENT, note))
                .isInstanceOf(EncryptedContentUnreadableException.class)
                .hasMessageContaining("No available master key");

        keys.put("master-2026", backup);

        assertThat(encryption("master-2027").decrypt(field, Purpose.NOTE_CONTENT, note)).isEqualTo(NOTE);
    }

    @Test
    void migratesContentFromRetiredMasterKeysToTransitAndAcrossTransitRotations() {
        UUID patient = UUID.randomUUID();
        UUID note = UUID.randomUUID();
        EncryptedField field = encryption("master-2026").encrypt(patient, Purpose.NOTE_CONTENT, note, NOTE);
        String transitKey = OpenBaoTestContainer.createKey("kek", "aes256-gcm96");
        ContentEncryption migrating = transitEncryption(transitKey, Map.copyOf(keys));

        assertThat(migrating.status().activeMasterKeyId()).isEqualTo(transitKey + "-v1");
        assertThat(migrating.status().availableMasterKeyIds()).containsExactlyInAnyOrder(transitKey + "-v1", "master-2026");
        assertThat(migrating.rewrapWithActiveMasterKey()).isEqualTo(1);
        assertThat(transitEncryption(transitKey, Map.of()).decrypt(field, Purpose.NOTE_CONTENT, note)).isEqualTo(NOTE);

        OpenBaoTestContainer.rotate(transitKey);
        ContentEncryption rotated = transitEncryption(transitKey, Map.of());
        EncryptedField afterRotation = rotated.encrypt(UUID.randomUUID(), Purpose.NOTE_CONTENT, note, NOTE);

        assertThat(rotated.status().pendingRewrap()).isEqualTo(1);
        assertThat(rotated.rewrapWithActiveMasterKey()).isEqualTo(1);
        assertThat(rotated.status().wrappingsPerMasterKey())
                .containsEntry("master-2026", 1L).containsEntry(transitKey + "-v1", 1L).containsEntry(transitKey + "-v2", 2L);
        assertThat(rotated.decrypt(field, Purpose.NOTE_CONTENT, note)).isEqualTo(NOTE);
        assertThat(rotated.decrypt(afterRotation, Purpose.NOTE_CONTENT, note)).isEqualTo(NOTE);
    }

    private ContentEncryption encryption(String activeKeyId) {
        MasterKeys masterKeys = MasterKeys.of(LocalKeyEncryptionKeys.active(activeKeyId, Map.copyOf(keys)), LocalKeyEncryptionKeys.retired(Map.of()));
        return new ContentEncryption(masterKeys, store, transactions, clock);
    }

    private ContentEncryption transitEncryption(String transitKey, Map<String, String> retired) {
        TransitKeys transitKeys = new TransitKeys(new TransitClient(OpenBaoTestContainer.template(), "transit"), transitKey, Duration.ofMinutes(5), clock);
        MasterKeys masterKeys = MasterKeys.of(new TransitKeyEncryptionKeys(transitKeys), LocalKeyEncryptionKeys.retired(retired));
        return new ContentEncryption(masterKeys, store, transactions, clock);
    }
}
