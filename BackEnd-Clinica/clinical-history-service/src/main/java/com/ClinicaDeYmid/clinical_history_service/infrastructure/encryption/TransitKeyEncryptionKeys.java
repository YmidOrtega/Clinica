package com.ClinicaDeYmid.clinical_history_service.infrastructure.encryption;

import com.ClinicaDeYmid.commons.openbao.transit.KeyVersion;
import com.ClinicaDeYmid.commons.openbao.transit.TransitKeys;
import com.ClinicaDeYmid.commons.openbao.transit.TransitRejectedException;

import java.util.Optional;
import java.util.Set;

final class TransitKeyEncryptionKeys implements KeyEncryptionKeys {

    static final String KEY_TYPE = "aes256-gcm96";

    private final TransitKeys keys;

    TransitKeyEncryptionKeys(TransitKeys keys) {
        this.keys = keys;
        keys.current().requireType(KEY_TYPE);
    }

    @Override
    public Optional<String> activeKeyId() {
        return Optional.of(keys.current().latest().id());
    }

    @Override
    public Set<String> keyIds() {
        return keys.current().usableVersions().keySet();
    }

    @Override
    public boolean knows(String keyId) {
        return KeyVersion.parse(keyId).map(version -> keys.refreshIfUnknown(version).usable(version)).orElse(false);
    }

    @Override
    public byte[] wrap(String keyId, byte[] dataKey, byte[] associatedData) {
        return keys.client().encrypt(version(keyId), dataKey, associatedData);
    }

    @Override
    public byte[] unwrap(String keyId, byte[] wrappedKey, byte[] associatedData) {
        try {
            return keys.client().decrypt(version(keyId), wrappedKey, associatedData);
        } catch (TransitRejectedException rejected) {
            throw new EncryptedContentUnreadableException("Data key wrapping does not open with transit key " + keyId, rejected);
        }
    }

    @Override
    public void refresh() {
        keys.refresh();
    }

    private static KeyVersion version(String keyId) {
        return KeyVersion.parse(keyId).orElseThrow(() -> new IllegalArgumentException("Not a transit key id: " + keyId));
    }
}
