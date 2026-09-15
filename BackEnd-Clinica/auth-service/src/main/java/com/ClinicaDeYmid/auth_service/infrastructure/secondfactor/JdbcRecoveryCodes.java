package com.ClinicaDeYmid.auth_service.infrastructure.secondfactor;

import com.ClinicaDeYmid.auth_service.domain.secondfactor.RecoveryCodes;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

class JdbcRecoveryCodes implements RecoveryCodes {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int GROUPS = 4;
    private static final int GROUP_LENGTH = 4;

    private final JdbcTemplate jdbc;

    JdbcRecoveryCodes(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<String> replaceAll(UUID userUuid, Instant now) {
        jdbc.update("UPDATE recovery_codes SET revoked_at = ? WHERE user_uuid = ? AND used_at IS NULL AND revoked_at IS NULL",
                Timestamp.from(now), userUuid.toString());
        List<String> codes = new ArrayList<>();
        for (int index = 0; index < CODES_PER_USER; index++) {
            String code = newCode();
            jdbc.update("INSERT INTO recovery_codes (id, user_uuid, code_hash, created_at) VALUES (?, ?, ?, ?)",
                    UUID.randomUUID().toString(), userUuid.toString(), hash(code), Timestamp.from(now));
            codes.add(code);
        }
        return List.copyOf(codes);
    }

    @Override
    public boolean use(UUID userUuid, String code, Instant now) {
        if (code == null || code.isBlank()) {
            return false;
        }
        return jdbc.update("""
                UPDATE recovery_codes SET used_at = ?
                WHERE user_uuid = ? AND code_hash = ? AND used_at IS NULL AND revoked_at IS NULL""",
                Timestamp.from(now), userUuid.toString(), hash(code)) == 1;
    }

    @Override
    public int remaining(UUID userUuid) {
        Integer remaining = jdbc.queryForObject("""
                SELECT COUNT(*) FROM recovery_codes WHERE user_uuid = ? AND used_at IS NULL AND revoked_at IS NULL""",
                Integer.class, userUuid.toString());
        return remaining == null ? 0 : remaining;
    }

    private static String newCode() {
        StringBuilder code = new StringBuilder();
        for (int index = 0; index < GROUPS * GROUP_LENGTH; index++) {
            if (index > 0 && index % GROUP_LENGTH == 0) {
                code.append('-');
            }
            code.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return code.toString();
    }

    static String hash(String code) {
        String normalized = code.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(normalized.getBytes(StandardCharsets.US_ASCII)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
