package com.ClinicaDeYmid.clinical_history_service.support;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.Base64;

public final class TestEncryptionKeys {

    public static final String ACTIVE_KEY_ID = "clinical-content-test";

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Path DIRECTORY = createDirectory();

    private TestEncryptionKeys() {
    }

    public static String directory() {
        return DIRECTORY.toString();
    }

    public static Path newDirectory() {
        try {
            return Files.createTempDirectory("clinical-encryption-keys");
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    public static void write(Path directory, String keyId) {
        byte[] key = new byte[32];
        RANDOM.nextBytes(key);
        try {
            Files.writeString(directory.resolve(keyId + ".key"), Base64.getEncoder().encodeToString(key) + "\n");
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    private static Path createDirectory() {
        Path directory = newDirectory();
        write(directory, ACTIVE_KEY_ID);
        return directory;
    }
}
