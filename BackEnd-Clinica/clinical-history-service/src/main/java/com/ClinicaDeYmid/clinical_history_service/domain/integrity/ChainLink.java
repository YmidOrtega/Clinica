package com.ClinicaDeYmid.clinical_history_service.domain.integrity;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

public record ChainLink(
        UUID patientUuid,
        long sequence,
        EntryType entryType,
        UUID entryId,
        int formatVersion,
        String payloadHash,
        String previousHash,
        String entryHash,
        String keyId,
        String seal,
        Instant sealedAt) {

    public static final String GENESIS_HASH = "0".repeat(64);

    private static final Pattern SHA_256_HEX = Pattern.compile("^[0-9a-f]{64}$");

    public ChainLink {
        Objects.requireNonNull(patientUuid, "patientUuid");
        Objects.requireNonNull(entryType, "entryType");
        Objects.requireNonNull(entryId, "entryId");
        Objects.requireNonNull(keyId, "keyId");
        Objects.requireNonNull(seal, "seal");
        Objects.requireNonNull(sealedAt, "sealedAt");
        if (sequence < 1) {
            throw new IllegalArgumentException("sequence must start at 1");
        }
        requireHash(payloadHash, "payloadHash");
        requireHash(previousHash, "previousHash");
        requireHash(entryHash, "entryHash");
    }

    public LedgerEntry.Key entryKey() {
        return new LedgerEntry.Key(entryType, entryId);
    }

    public static long nextSequenceAfter(ChainLink previous) {
        return previous == null ? 1 : previous.sequence + 1;
    }

    public static String hashAfter(ChainLink previous) {
        return previous == null ? GENESIS_HASH : previous.entryHash;
    }

    private static void requireHash(String value, String name) {
        if (value == null || !SHA_256_HEX.matcher(value).matches()) {
            throw new IllegalArgumentException(name + " must be a lowercase SHA-256 hex digest");
        }
    }
}
