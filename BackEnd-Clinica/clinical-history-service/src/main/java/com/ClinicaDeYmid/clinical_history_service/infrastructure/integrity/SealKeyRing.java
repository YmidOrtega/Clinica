package com.ClinicaDeYmid.clinical_history_service.infrastructure.integrity;

import java.security.GeneralSecurityException;
import java.security.PublicKey;
import java.security.Signature;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.regex.Pattern;

final class SealKeyRing {

    static final String ALGORITHM = "SHA256withECDSA";

    private static final Pattern KEY_ID = Pattern.compile("^[A-Za-z0-9_-]{1,64}$");

    private final SealSigner signer;
    private final Map<String, PublicKey> retired;

    private SealKeyRing(SealSigner signer, Map<String, PublicKey> retired) {
        this.signer = signer;
        this.retired = Collections.unmodifiableMap(retired);
    }

    static SealKeyRing of(SealSigner signer, Map<String, String> retiredPublicKeysPem) {
        Map<String, PublicKey> retired = new TreeMap<>();
        (retiredPublicKeysPem == null ? Map.<String, String>of() : retiredPublicKeysPem).forEach((keyId, pem) -> {
            if (!KEY_ID.matcher(keyId).matches()) {
                throw new IllegalStateException("Retired seal key id " + keyId + " has invalid characters");
            }
            retired.put(keyId, EcPublicKeys.parseP256(pem, keyId));
        });
        if (retired.containsKey(signer.activeKeyId())) {
            throw new IllegalStateException("Retired seal key " + signer.activeKeyId() + " collides with the active key");
        }
        return new SealKeyRing(signer, retired);
    }

    String activeKeyId() {
        return signer.activeKeyId();
    }

    byte[] sign(String keyId, byte[] data) {
        return signer.sign(keyId, data);
    }

    boolean knows(String keyId) {
        return publicKey(keyId).isPresent();
    }

    Optional<Boolean> verify(String keyId, byte[] data, byte[] seal) {
        return publicKey(keyId).map(key -> {
            try {
                Signature verifier = Signature.getInstance(ALGORITHM);
                verifier.initVerify(key);
                verifier.update(data);
                return verifier.verify(seal);
            } catch (GeneralSecurityException malformedSeal) {
                return false;
            }
        });
    }

    Map<String, String> publicKeysPem() {
        Map<String, String> pem = new TreeMap<>();
        retired.forEach((keyId, key) -> pem.put(keyId, EcPublicKeys.pem(key)));
        signer.publicKeys().forEach((keyId, key) -> pem.put(keyId, EcPublicKeys.pem(key)));
        return pem;
    }

    private Optional<PublicKey> publicKey(String keyId) {
        PublicKey retiredKey = retired.get(keyId);
        return retiredKey != null ? Optional.of(retiredKey) : signer.publicKey(keyId);
    }
}
