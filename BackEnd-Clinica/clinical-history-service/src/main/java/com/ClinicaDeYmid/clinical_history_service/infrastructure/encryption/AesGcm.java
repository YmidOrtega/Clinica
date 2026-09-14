package com.ClinicaDeYmid.clinical_history_service.infrastructure.encryption;

import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;

final class AesGcm {

    static final int KEY_BYTES = 32;
    static final byte FORMAT_VERSION = 1;

    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final SecureRandom RANDOM = new SecureRandom();

    private AesGcm() {
    }

    static byte[] randomKey() {
        byte[] key = new byte[KEY_BYTES];
        RANDOM.nextBytes(key);
        return key;
    }

    static byte[] encrypt(SecretKey key, byte[] plaintext, String associatedData) {
        byte[] iv = new byte[IV_BYTES];
        RANDOM.nextBytes(iv);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            cipher.updateAAD(associatedData.getBytes(StandardCharsets.UTF_8));
            byte[] sealed = cipher.doFinal(plaintext);
            return ByteBuffer.allocate(1 + IV_BYTES + sealed.length).put(FORMAT_VERSION).put(iv).put(sealed).array();
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("AES-GCM encryption failed", ex);
        }
    }

    static byte[] decrypt(SecretKey key, byte[] envelope, String associatedData) throws AEADBadTagException {
        if (envelope == null || envelope.length < 1 + IV_BYTES + TAG_BITS / 8 || envelope[0] != FORMAT_VERSION) {
            throw new AEADBadTagException("Unknown or truncated envelope");
        }
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, Arrays.copyOfRange(envelope, 1, 1 + IV_BYTES)));
            cipher.updateAAD(associatedData.getBytes(StandardCharsets.UTF_8));
            return cipher.doFinal(envelope, 1 + IV_BYTES, envelope.length - 1 - IV_BYTES);
        } catch (AEADBadTagException tampered) {
            throw tampered;
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("AES-GCM decryption failed", ex);
        }
    }
}
