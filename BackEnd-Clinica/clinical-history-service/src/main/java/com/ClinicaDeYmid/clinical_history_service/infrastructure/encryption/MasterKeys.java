package com.ClinicaDeYmid.clinical_history_service.infrastructure.encryption;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

final class MasterKeys {

    record Wrapping(String masterKeyId, byte[] wrappedKey) {
    }

    private final KeyEncryptionKeys active;
    private final KeyEncryptionKeys retired;

    private MasterKeys(KeyEncryptionKeys active, KeyEncryptionKeys retired) {
        this.active = active;
        this.retired = retired;
    }

    static MasterKeys of(KeyEncryptionKeys active, KeyEncryptionKeys retired) {
        if (active.activeKeyId().isEmpty()) {
            throw new IllegalStateException("Clinical content needs an active master key");
        }
        return new MasterKeys(active, retired);
    }

    String activeKeyId() {
        return active.activeKeyId().orElseThrow();
    }

    Set<String> availableKeyIds() {
        Set<String> ids = new TreeSet<>(active.keyIds());
        ids.addAll(retired.keyIds());
        return Collections.unmodifiableSet(ids);
    }

    boolean knows(String keyId) {
        return active.knows(keyId) || retired.knows(keyId);
    }

    Wrapping wrap(UUID dataKeyId, UUID patientUuid, byte[] dataKey) {
        String keyId = activeKeyId();
        return new Wrapping(keyId, active.wrap(keyId, dataKey, associatedData(dataKeyId, patientUuid, keyId)));
    }

    Optional<byte[]> unwrap(String masterKeyId, UUID dataKeyId, UUID patientUuid, byte[] wrappedKey) {
        byte[] associatedData = associatedData(dataKeyId, patientUuid, masterKeyId);
        if (active.knows(masterKeyId)) {
            return Optional.of(active.unwrap(masterKeyId, wrappedKey, associatedData));
        }
        if (retired.knows(masterKeyId)) {
            return Optional.of(retired.unwrap(masterKeyId, wrappedKey, associatedData));
        }
        return Optional.empty();
    }

    void refresh() {
        active.refresh();
    }

    private static byte[] associatedData(UUID dataKeyId, UUID patientUuid, String masterKeyId) {
        return ("clinica.clinical.data-key/v1|" + dataKeyId + "|" + patientUuid + "|" + masterKeyId).getBytes(StandardCharsets.UTF_8);
    }
}
