package com.ClinicaDeYmid.clinical_history_service.support;

import java.security.SecureRandom;
import java.util.Base64;

public final class TestEncryptionKeys {

    private static final SecureRandom RANDOM = new SecureRandom();

    private TestEncryptionKeys() {
    }

    public static String randomBase64() {
        byte[] key = new byte[32];
        RANDOM.nextBytes(key);
        return Base64.getEncoder().encodeToString(key);
    }
}
