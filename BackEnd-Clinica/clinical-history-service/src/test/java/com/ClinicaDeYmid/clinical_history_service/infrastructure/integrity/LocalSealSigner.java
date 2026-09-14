package com.ClinicaDeYmid.clinical_history_service.infrastructure.integrity;

import com.ClinicaDeYmid.clinical_history_service.support.TestSealKeys;

import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.PublicKey;
import java.security.Signature;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

final class LocalSealSigner implements SealSigner {

    private final Map<String, KeyPair> pairs = new TreeMap<>();
    private String activeKeyId;

    LocalSealSigner(String keyId) {
        rotateTo(keyId);
    }

    void rotateTo(String keyId) {
        pairs.put(keyId, TestSealKeys.generate());
        activeKeyId = keyId;
    }

    String publicPem(String keyId) {
        return TestSealKeys.publicPem(pairs.get(keyId).getPublic());
    }

    @Override
    public String activeKeyId() {
        return activeKeyId;
    }

    @Override
    public byte[] sign(String keyId, byte[] data) {
        try {
            Signature signer = Signature.getInstance(SealKeyRing.ALGORITHM);
            signer.initSign(pairs.get(keyId).getPrivate());
            signer.update(data);
            return signer.sign();
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException(ex);
        }
    }

    @Override
    public Optional<PublicKey> publicKey(String keyId) {
        return Optional.ofNullable(pairs.get(keyId)).map(KeyPair::getPublic);
    }

    @Override
    public Map<String, PublicKey> publicKeys() {
        Map<String, PublicKey> keys = new TreeMap<>();
        pairs.forEach((keyId, pair) -> keys.put(keyId, pair.getPublic()));
        return keys;
    }
}
