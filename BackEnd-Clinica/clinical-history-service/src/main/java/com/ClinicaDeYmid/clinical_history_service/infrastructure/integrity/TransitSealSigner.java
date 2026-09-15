package com.ClinicaDeYmid.clinical_history_service.infrastructure.integrity;

import com.ClinicaDeYmid.commons.openbao.transit.KeyVersion;
import com.ClinicaDeYmid.commons.openbao.transit.SignatureFormat;
import com.ClinicaDeYmid.commons.openbao.transit.TransitKey;
import com.ClinicaDeYmid.commons.openbao.transit.TransitKeys;

import java.security.PublicKey;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

final class TransitSealSigner implements SealSigner {

    static final String KEY_TYPE = "ecdsa-p256";

    private final TransitKeys keys;
    private final ConcurrentMap<String, PublicKey> parsed = new ConcurrentHashMap<>();

    TransitSealSigner(TransitKeys keys) {
        this.keys = keys;
        keys.current().requireType(KEY_TYPE);
    }

    @Override
    public String activeKeyId() {
        return keys.current().latest().id();
    }

    @Override
    public byte[] sign(String keyId, byte[] data) {
        KeyVersion version = KeyVersion.parse(keyId).orElseThrow(() -> new IllegalArgumentException("Not a transit key id: " + keyId));
        return keys.client().sign(version, data, SignatureFormat.DER);
    }

    @Override
    public Optional<PublicKey> publicKey(String keyId) {
        return KeyVersion.parse(keyId).flatMap(version -> {
            TransitKey key = keys.refreshIfUnknown(version);
            String pem = key.usable(version) ? key.publicKeysPem().get(version.version()) : null;
            return Optional.ofNullable(pem).map(value -> parsed.computeIfAbsent(keyId, id -> EcPublicKeys.parseP256(value, id)));
        });
    }

    @Override
    public Map<String, PublicKey> publicKeys() {
        Map<String, PublicKey> all = new TreeMap<>();
        keys.current().usableVersions().keySet().forEach(keyId -> publicKey(keyId).ifPresent(key -> all.put(keyId, key)));
        return all;
    }
}
