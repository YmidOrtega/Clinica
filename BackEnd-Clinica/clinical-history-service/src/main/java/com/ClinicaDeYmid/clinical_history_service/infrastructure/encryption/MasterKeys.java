package com.ClinicaDeYmid.clinical_history_service.infrastructure.encryption;

import javax.crypto.AEADBadTagException;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Stream;

final class MasterKeys {

    private static final String SUFFIX = ".key";
    private static final Pattern KEY_ID = Pattern.compile("^[A-Za-z0-9._-]{1,64}$");

    private final String activeKeyId;
    private final Map<String, SecretKey> keys;

    private MasterKeys(String activeKeyId, Map<String, SecretKey> keys) {
        this.activeKeyId = activeKeyId;
        this.keys = Collections.unmodifiableMap(keys);
    }

    static MasterKeys load(EncryptionProperties properties) {
        if (properties.keysLocation() == null || properties.activeKeyId() == null || properties.activeKeyId().isBlank()) {
            throw new IllegalStateException("clinica.clinical.encryption.keys-location and active-key-id are required to protect clinical content");
        }
        String activeKeyId = properties.activeKeyId().strip();
        Map<String, SecretKey> keys = new TreeMap<>();
        try (Stream<Path> files = Files.list(properties.keysLocation())) {
            for (Path file : files.filter(path -> path.getFileName().toString().endsWith(SUFFIX)).toList()) {
                String name = file.getFileName().toString();
                String keyId = name.substring(0, name.length() - SUFFIX.length());
                if (KEY_ID.matcher(keyId).matches()) {
                    keys.put(keyId, read(file));
                }
            }
        } catch (IOException ex) {
            throw new UncheckedIOException("Could not read the master keys directory", ex);
        }
        if (!keys.containsKey(activeKeyId)) {
            throw new IllegalStateException("The active master key " + activeKeyId + " is not in the keys directory");
        }
        return new MasterKeys(activeKeyId, keys);
    }

    String activeKeyId() {
        return activeKeyId;
    }

    Set<String> availableKeyIds() {
        return keys.keySet();
    }

    boolean knows(String keyId) {
        return keys.containsKey(keyId);
    }

    byte[] wrap(UUID dataKeyId, UUID patientUuid, byte[] dataKey) {
        return AesGcm.encrypt(keys.get(activeKeyId), dataKey, associatedData(dataKeyId, patientUuid, activeKeyId));
    }

    Optional<byte[]> unwrap(String masterKeyId, UUID dataKeyId, UUID patientUuid, byte[] wrappedKey) {
        SecretKey key = keys.get(masterKeyId);
        if (key == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(AesGcm.decrypt(key, wrappedKey, associatedData(dataKeyId, patientUuid, masterKeyId)));
        } catch (AEADBadTagException tampered) {
            throw new EncryptedContentUnreadableException("Data key " + dataKeyId + " does not unwrap with master key " + masterKeyId);
        }
    }

    private static String associatedData(UUID dataKeyId, UUID patientUuid, String masterKeyId) {
        return "clinica.clinical.data-key/v1|" + dataKeyId + "|" + patientUuid + "|" + masterKeyId;
    }

    private static SecretKey read(Path file) {
        try {
            byte[] raw = Base64.getDecoder().decode(Files.readString(file, StandardCharsets.US_ASCII).strip());
            if (raw.length != AesGcm.KEY_BYTES) {
                throw new IllegalStateException("Master key " + file.getFileName() + " must be 32 random bytes encoded in Base64");
            }
            return new SecretKeySpec(raw, "AES");
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("Master key " + file.getFileName() + " is not valid Base64", ex);
        } catch (IOException ex) {
            throw new UncheckedIOException("Could not read " + file.getFileName(), ex);
        }
    }
}
