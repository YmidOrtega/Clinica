package com.ClinicaDeYmid.commons.documents;

import com.ClinicaDeYmid.commons.openbao.transit.TransitClient;
import com.ClinicaDeYmid.commons.openbao.transit.TransitKeys;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;

public final class TransitDocumentSealer implements DocumentSealer {

    private final SealKeyRing keys;
    private final String purpose;

    public TransitDocumentSealer(SealKeyRing keys, String purpose) {
        this.keys = keys;
        this.purpose = Documents.requirePurpose(purpose);
    }

    public static TransitDocumentSealer with(TransitClient transit, String transitKey, Duration keyRefreshInterval,
                                             Clock clock, Map<String, String> retiredPublicKeys, String purpose) {
        TransitKeys keys = new TransitKeys(transit, transitKey, keyRefreshInterval, clock);
        return new TransitDocumentSealer(SealKeyRing.of(new TransitSealSigner(keys), retiredPublicKeys), purpose);
    }

    @Override
    public DocumentSeal seal(String sha256) {
        String keyId = keys.activeKeyId();
        return new DocumentSeal(keyId, Base64.getEncoder().encodeToString(keys.sign(keyId, sealed(sha256))));
    }

    @Override
    public boolean verify(String sha256, DocumentSeal seal) {
        return decode(seal.value())
                .flatMap(value -> keys.verify(seal.keyId(), sealed(sha256), value))
                .orElse(false);
    }

    @Override
    public String activeKeyId() {
        return keys.activeKeyId();
    }

    @Override
    public Map<String, String> publicKeysPem() {
        return keys.publicKeysPem();
    }

    private byte[] sealed(String sha256) {
        return (purpose + "|" + Documents.requireSha256(sha256)).getBytes(StandardCharsets.US_ASCII);
    }

    private static Optional<byte[]> decode(String value) {
        try {
            return Optional.of(Base64.getDecoder().decode(value));
        } catch (IllegalArgumentException malformed) {
            return Optional.empty();
        }
    }
}
