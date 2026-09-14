package com.ClinicaDeYmid.clinical_history_service.support;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.spec.ECGenParameterSpec;
import java.util.Base64;

public final class TestSealKeys {

    public static final String ACTIVE_KEY_ID = "clinical-seal-test";

    private static final Path DIRECTORY = createDirectory();

    private TestSealKeys() {
    }

    public static String directory() {
        return DIRECTORY.toString();
    }

    public static Path newDirectory() {
        try {
            return Files.createTempDirectory("clinical-seal-keys");
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    public static void write(Path directory, String keyId, boolean withPrivateKey) {
        write(directory, keyId, withPrivateKey, "secp256r1");
    }

    public static void write(Path directory, String keyId, boolean withPrivateKey, String curve) {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
            generator.initialize(new ECGenParameterSpec(curve));
            KeyPair pair = generator.generateKeyPair();
            Files.writeString(directory.resolve(keyId + ".public.pem"), pem("PUBLIC KEY", pair.getPublic().getEncoded()));
            if (withPrivateKey) {
                Files.writeString(directory.resolve(keyId + ".private.pem"), pem("PRIVATE KEY", pair.getPrivate().getEncoded()));
            }
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException(ex);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    private static Path createDirectory() {
        Path directory = newDirectory();
        write(directory, ACTIVE_KEY_ID, true);
        return directory;
    }

    private static String pem(String type, byte[] der) {
        return "-----BEGIN " + type + "-----\n"
                + Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII)).encodeToString(der)
                + "\n-----END " + type + "-----\n";
    }
}
