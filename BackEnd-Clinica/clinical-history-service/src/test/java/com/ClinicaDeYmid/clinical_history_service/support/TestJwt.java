package com.ClinicaDeYmid.clinical_history_service.support;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

public final class TestJwt {

    private static final KeyPair KEYS = generate();

    private TestJwt() {
    }

    public static String publicKeyBase64() {
        return Base64.getEncoder().encodeToString(KEYS.getPublic().getEncoded());
    }

    private static KeyPair generate() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
