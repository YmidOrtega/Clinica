package com.ClinicaDeYmid.auth_service.infrastructure.persistence;

import com.ClinicaDeYmid.auth_service.domain.onetime.OneTimeTokenPurpose;
import com.ClinicaDeYmid.auth_service.domain.onetime.OneTimeTokens;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

@Repository
class JdbcOneTimeTokens implements OneTimeTokens {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int TOKEN_BYTES = 32;

    private final JdbcTemplate jdbc;

    JdbcOneTimeTokens(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public String issue(UUID userUuid, OneTimeTokenPurpose purpose, Instant now) {
        jdbc.update("""
                UPDATE auth_sessions.one_time_tokens SET consumed_at = ?
                WHERE user_uuid = ? AND purpose = ? AND consumed_at IS NULL""", Timestamp.from(now), userUuid.toString(), purpose.name());
        byte[] random = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(random);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(random);
        jdbc.update("""
                INSERT INTO auth_sessions.one_time_tokens (id, user_uuid, purpose, token_hash, created_at, expires_at)
                VALUES (?, ?, ?, ?, ?, ?)""", UUID.randomUUID().toString(), userUuid.toString(), purpose.name(), TokenHashes.of(token),
                Timestamp.from(now), Timestamp.from(now.plus(purpose.lifetime())));
        return token;
    }

    @Override
    public Optional<UUID> consume(String token, OneTimeTokenPurpose purpose, Instant now) {
        if (token == null || token.isBlank() || token.startsWith(TokenHashes.STORED_PREFIX)) {
            return Optional.empty();
        }
        String hash = TokenHashes.of(token);
        int consumed = jdbc.update("""
                UPDATE auth_sessions.one_time_tokens SET consumed_at = ?
                WHERE token_hash = ? AND purpose = ? AND consumed_at IS NULL AND expires_at > ?""",
                Timestamp.from(now), hash, purpose.name(), Timestamp.from(now));
        if (consumed == 0) {
            return Optional.empty();
        }
        return jdbc.queryForList("SELECT user_uuid FROM auth_sessions.one_time_tokens WHERE token_hash = ?", String.class, hash)
                .stream().findFirst().map(UUID::fromString);
    }
}
