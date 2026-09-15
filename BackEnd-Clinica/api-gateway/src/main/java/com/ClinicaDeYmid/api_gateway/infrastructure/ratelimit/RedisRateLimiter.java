package com.ClinicaDeYmid.api_gateway.infrastructure.ratelimit;

import com.ClinicaDeYmid.api_gateway.domain.ratelimit.RateLimitDecision;
import com.ClinicaDeYmid.api_gateway.domain.ratelimit.RateLimitPolicy;
import com.ClinicaDeYmid.api_gateway.domain.ratelimit.RateLimiter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;

@Component
class RedisRateLimiter implements RateLimiter {

    private static final Logger log = LoggerFactory.getLogger(RedisRateLimiter.class);
    private static final String KEY_PREFIX = "clinica:gateway:rate-limit:";
    @SuppressWarnings("rawtypes")
    private static final RedisScript<List> FIXED_WINDOW = RedisScript.of("""
            local count = redis.call('INCR', KEYS[1])
            if count == 1 then
              redis.call('PEXPIRE', KEYS[1], ARGV[1])
            end
            return {count, redis.call('PTTL', KEYS[1])}""", List.class);

    private final StringRedisTemplate redis;

    RedisRateLimiter(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public RateLimitDecision acquire(RateLimitPolicy policy, String subject) {
        try {
            List<?> result = redis.execute(FIXED_WINDOW, List.of(KEY_PREFIX + policy.name() + ":" + sha256(subject)),
                    Long.toString(policy.window().toMillis()));
            long count = ((Number) result.get(0)).longValue();
            long untilReset = ((Number) result.get(1)).longValue();
            return policy.decide(count, Duration.ofMillis(Math.max(untilReset, 0)));
        } catch (DataAccessException unavailable) {
            log.warn("Rate limit {} not applied: Redis is unavailable ({})", policy.name(), unavailable.getClass().getSimpleName());
            return new RateLimitDecision.Unknown();
        }
    }

    private static String sha256(String subject) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(subject.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
