package com.ClinicaDeYmid.clinical_history_service.infrastructure.encryption;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.crypto.AEADBadTagException;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public class ContentEncryption {

    public enum Purpose {
        NOTE_CONTENT,
        DRAFT_CONTENT,
        VOID_REASON
    }

    public record EncryptedField(UUID dataKeyId, byte[] ciphertext) {

        public EncryptedField {
            Objects.requireNonNull(dataKeyId, "dataKeyId");
            Objects.requireNonNull(ciphertext, "ciphertext");
        }
    }

    public record Status(String activeMasterKeyId, Set<String> availableMasterKeyIds, long dataKeys, long pendingRewrap,
                         Map<String, Long> wrappingsPerMasterKey) {

        public boolean retiredKeysCanBeRemoved() {
            return pendingRewrap == 0;
        }
    }

    private static final Logger log = LoggerFactory.getLogger(ContentEncryption.class);
    private static final int REWRAP_BATCH = 500;

    private final MasterKeys masterKeys;
    private final DataKeyStore store;
    private final TransactionOperations transactions;
    private final Clock clock;
    private final Cache<UUID, SecretKey> dataKeys = Caffeine.newBuilder()
            .maximumSize(10_000)
            .expireAfterAccess(Duration.ofMinutes(10))
            .build();
    private final Cache<UUID, UUID> dataKeyOfPatient = Caffeine.newBuilder()
            .maximumSize(10_000)
            .expireAfterAccess(Duration.ofMinutes(10))
            .build();

    ContentEncryption(MasterKeys masterKeys, DataKeyStore store, TransactionOperations transactions, Clock clock) {
        this.masterKeys = masterKeys;
        this.store = store;
        this.transactions = transactions;
        this.clock = clock;
    }

    public EncryptedField encrypt(UUID patientUuid, Purpose purpose, UUID recordId, byte[] plaintext) {
        DataKey dataKey = dataKeyFor(patientUuid);
        return new EncryptedField(dataKey.id(), AesGcm.encrypt(dataKey.key(), plaintext, associatedData(purpose, recordId, dataKey.id())));
    }

    public byte[] decrypt(EncryptedField field, Purpose purpose, UUID recordId) {
        try {
            return AesGcm.decrypt(dataKey(field.dataKeyId()), field.ciphertext(), associatedData(purpose, recordId, field.dataKeyId()));
        } catch (AEADBadTagException tampered) {
            throw new EncryptedContentUnreadableException("Encrypted " + purpose + " of record " + recordId
                    + " failed authentication; it was altered or belongs to another record");
        }
    }

    public Status status() {
        String active = masterKeys.activeKeyId();
        return new Status(active, masterKeys.availableKeyIds(), store.countDataKeys(), store.countNotWrappedBy(active),
                store.wrappingsPerMasterKey());
    }

    public long rewrapWithActiveMasterKey() {
        String active = masterKeys.activeKeyId();
        long rewrapped = 0;
        while (true) {
            Long batch = transactions.execute(status -> rewrapBatch(active));
            if (batch == null || batch == 0) {
                break;
            }
            rewrapped += batch;
        }
        log.info("Rewrapped {} data keys with master key {}", rewrapped, active);
        return rewrapped;
    }

    private long rewrapBatch(String active) {
        List<UUID> pendingKeys = store.dataKeysNotWrappedBy(active, REWRAP_BATCH);
        Instant now = Instant.now(clock);
        for (UUID dataKeyId : pendingKeys) {
            DataKeyStore.Wrapping source = readableWrapping(dataKeyId);
            byte[] raw = unwrap(source);
            try {
                store.insertWrapping(dataKeyId, active, masterKeys.wrap(dataKeyId, source.patientUuid(), raw), now);
            } finally {
                Arrays.fill(raw, (byte) 0);
            }
        }
        return pendingKeys.size();
    }

    private record DataKey(UUID id, SecretKey key) {
    }

    private DataKey dataKeyFor(UUID patientUuid) {
        UUID known = dataKeyOfPatient.getIfPresent(patientUuid);
        if (known == null) {
            known = store.dataKeyOf(patientUuid).orElse(null);
        }
        if (known != null) {
            dataKeyOfPatient.put(patientUuid, known);
            return new DataKey(known, dataKey(known));
        }
        UUID dataKeyId = UUID.randomUUID();
        Instant now = Instant.now(clock);
        if (!store.insertDataKey(dataKeyId, patientUuid, now)) {
            UUID winner = store.dataKeyOf(patientUuid)
                    .orElseThrow(() -> new IllegalStateException("The data key of patient " + patientUuid + " could not be created"));
            return new DataKey(winner, dataKey(winner));
        }
        byte[] raw = AesGcm.randomKey();
        try {
            store.insertWrapping(dataKeyId, masterKeys.activeKeyId(), masterKeys.wrap(dataKeyId, patientUuid, raw), now);
            SecretKey key = new SecretKeySpec(raw, "AES");
            afterCommit(() -> {
                dataKeys.put(dataKeyId, key);
                dataKeyOfPatient.put(patientUuid, dataKeyId);
            });
            log.info("Created the data key of patient {}", patientUuid);
            return new DataKey(dataKeyId, key);
        } finally {
            Arrays.fill(raw, (byte) 0);
        }
    }

    private SecretKey dataKey(UUID dataKeyId) {
        SecretKey cached = dataKeys.getIfPresent(dataKeyId);
        if (cached != null) {
            return cached;
        }
        byte[] raw = unwrap(readableWrapping(dataKeyId));
        try {
            SecretKey key = new SecretKeySpec(raw, "AES");
            dataKeys.put(dataKeyId, key);
            return key;
        } finally {
            Arrays.fill(raw, (byte) 0);
        }
    }

    private byte[] unwrap(DataKeyStore.Wrapping wrapping) {
        return masterKeys.unwrap(wrapping.masterKeyId(), wrapping.dataKeyId(), wrapping.patientUuid(), wrapping.wrappedKey())
                .orElseThrow(() -> new EncryptedContentUnreadableException("Master key " + wrapping.masterKeyId() + " is not available"));
    }

    private DataKeyStore.Wrapping readableWrapping(UUID dataKeyId) {
        String active = masterKeys.activeKeyId();
        return store.wrappingsOf(dataKeyId).stream()
                .filter(wrapping -> masterKeys.knows(wrapping.masterKeyId()))
                .min(Comparator.comparing(wrapping -> !wrapping.masterKeyId().equals(active)))
                .orElseThrow(() -> new EncryptedContentUnreadableException("No available master key can unwrap data key " + dataKeyId));
    }

    private static void afterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }

    private static String associatedData(Purpose purpose, UUID recordId, UUID dataKeyId) {
        return "clinica.clinical.content/v1|" + purpose + "|" + recordId + "|" + dataKeyId;
    }
}
