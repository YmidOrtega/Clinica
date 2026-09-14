package com.ClinicaDeYmid.clinical_history_service.infrastructure.encryption;

import javax.crypto.AEADBadTagException;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;

final class LocalKeyEncryptionKeys implements KeyEncryptionKeys {

    private static final Pattern KEY_ID = Pattern.compile("^[A-Za-z0-9_-]{1,64}$");

    private final String activeKeyId;
    private final Map<String, SecretKey> keys;

    private LocalKeyEncryptionKeys(String activeKeyId, Map<String, SecretKey> keys) {
        this.activeKeyId = activeKeyId;
        this.keys = Collections.unmodifiableMap(keys);
    }

    static LocalKeyEncryptionKeys retired(Map<String, String> base64Keys) {
        return new LocalKeyEncryptionKeys(null, decode(base64Keys));
    }

    static LocalKeyEncryptionKeys active(String activeKeyId, Map<String, String> base64Keys) {
        Map<String, SecretKey> keys = decode(base64Keys);
        if (!keys.containsKey(activeKeyId)) {
            throw new IllegalStateException("The active master key " + activeKeyId + " is not among the provided keys");
        }
        return new LocalKeyEncryptionKeys(activeKeyId, keys);
    }

    @Override
    public Optional<String> activeKeyId() {
        return Optional.ofNullable(activeKeyId);
    }

    @Override
    public Set<String> keyIds() {
        return keys.keySet();
    }

    @Override
    public boolean knows(String keyId) {
        return keys.containsKey(keyId);
    }

    @Override
    public byte[] wrap(String keyId, byte[] dataKey, byte[] associatedData) {
        if (!keyId.equals(activeKeyId)) {
            throw new IllegalStateException("Master key " + keyId + " is retired and only unwraps");
        }
        return AesGcm.encrypt(keys.get(keyId), dataKey, associatedData);
    }

    @Override
    public byte[] unwrap(String keyId, byte[] wrappedKey, byte[] associatedData) {
        try {
            return AesGcm.decrypt(keys.get(keyId), wrappedKey, associatedData);
        } catch (AEADBadTagException tampered) {
            throw new EncryptedContentUnreadableException("Data key wrapping does not open with master key " + keyId);
        }
    }

    private static Map<String, SecretKey> decode(Map<String, String> base64Keys) {
        Map<String, SecretKey> keys = new TreeMap<>();
        (base64Keys == null ? Map.<String, String>of() : base64Keys).forEach((keyId, encoded) -> {
            if (!KEY_ID.matcher(keyId).matches()) {
                throw new IllegalStateException("Master key id " + keyId + " has invalid characters");
            }
            try {
                byte[] raw = Base64.getDecoder().decode(encoded.strip());
                if (raw.length != AesGcm.KEY_BYTES) {
                    throw new IllegalStateException("Master key " + keyId + " must be 32 random bytes encoded in Base64");
                }
                keys.put(keyId, new SecretKeySpec(raw, "AES"));
            } catch (IllegalArgumentException malformed) {
                throw new IllegalStateException("Master key " + keyId + " is not valid Base64", malformed);
            }
        });
        return keys;
    }
}
