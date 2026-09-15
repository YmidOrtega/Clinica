package com.ClinicaDeYmid.auth_service.infrastructure.persistence;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

final class TokenHashes {

    static final String STORED_PREFIX = "sha256:";

    private TokenHashes() {
    }

    static String of(String value) {
        if (value.startsWith(STORED_PREFIX)) {
            return value.substring(STORED_PREFIX.length());
        }
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    static String placeholder(String hash) {
        return STORED_PREFIX + hash;
    }
}
