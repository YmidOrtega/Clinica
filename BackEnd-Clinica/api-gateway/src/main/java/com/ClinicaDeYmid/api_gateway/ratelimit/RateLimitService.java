package com.ClinicaDeYmid.api_gateway.ratelimit;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class RateLimitService {

    private final RedisTemplate<String, String> redisTemplate;

    private static final long USER_RATE_LIMIT = 100;
    private static final long IP_RATE_LIMIT = 1000;
    private static final long WINDOW_SECONDS = 60;

    public RateLimitService(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public Mono<Boolean> allowUser(String userId) {
        if (userId == null || userId.isEmpty()) {
            return Mono.just(true);
        }
        return checkRedisLimit("rate_limit:user:" + userId, USER_RATE_LIMIT);
    }

    public Mono<Boolean> allowIp(String ipAddress) {
        if (ipAddress == null || ipAddress.isEmpty()) {
            return Mono.just(true);
        }
        return checkRedisLimit("rate_limit:ip:" + ipAddress, IP_RATE_LIMIT);
    }

    private Mono<Boolean> checkRedisLimit(String key, long limit) {
        return Mono.fromCallable(() -> {
            Long count = redisTemplate.opsForValue().increment(key);
            if (count == null) return true;
            if (count == 1) {
                redisTemplate.expire(key, WINDOW_SECONDS, TimeUnit.SECONDS);
            }
            boolean allowed = count <= limit;
            if (!allowed) {
                log.warn("Rate limit excedido para clave: {}", key);
            }
            return allowed;
        }).onErrorResume(e -> {
            log.error("Error en Redis al verificar rate limit para {}: {}", key, e.getMessage());
            return Mono.just(true);
        });
    }

    public Mono<Long> getRemainingForUser(String userId) {
        if (userId == null || userId.isEmpty()) return Mono.just(USER_RATE_LIMIT);
        return getRemaining("rate_limit:user:" + userId, USER_RATE_LIMIT);
    }

    public Mono<Long> getRemainingForIp(String ipAddress) {
        if (ipAddress == null || ipAddress.isEmpty()) return Mono.just(IP_RATE_LIMIT);
        return getRemaining("rate_limit:ip:" + ipAddress, IP_RATE_LIMIT);
    }

    private Mono<Long> getRemaining(String key, long limit) {
        return Mono.fromCallable(() -> {
            String val = redisTemplate.opsForValue().get(key);
            if (val == null) return limit;
            return Math.max(0, limit - Long.parseLong(val));
        }).onErrorReturn(limit);
    }
}
