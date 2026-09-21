package com.ClinicaDeYmid.commons.documents;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.regex.Pattern;

public final class Documents {

    private static final Pattern SHA256 = Pattern.compile("^[0-9a-f]{64}$");
    private static final Pattern PURPOSE = Pattern.compile("^[a-z0-9.-]+/v[0-9]+$");

    private Documents() {
    }

    public static String sha256(byte[] document) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(document));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    public static String sha256(String text) {
        return sha256(text.getBytes(StandardCharsets.UTF_8));
    }

    public static String requireSha256(String value) {
        if (value == null || !SHA256.matcher(value).matches()) {
            throw new IllegalArgumentException("Not a SHA-256 fingerprint in lowercase hexadecimal");
        }
        return value;
    }

    public static String requirePurpose(String value) {
        if (value == null || !PURPOSE.matcher(value).matches()) {
            throw new IllegalArgumentException("A document purpose looks like 'clinica.admissions.receipt/v1'");
        }
        return value;
    }
}
