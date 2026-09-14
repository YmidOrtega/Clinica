package com.ClinicaDeYmid.clinical_history_service.infrastructure.encryption;

import java.util.Optional;
import java.util.Set;

sealed interface KeyEncryptionKeys permits TransitKeyEncryptionKeys, LocalKeyEncryptionKeys {

    Optional<String> activeKeyId();

    Set<String> keyIds();

    boolean knows(String keyId);

    byte[] wrap(String keyId, byte[] dataKey, byte[] associatedData);

    byte[] unwrap(String keyId, byte[] wrappedKey, byte[] associatedData);

    default void refresh() {
    }
}
