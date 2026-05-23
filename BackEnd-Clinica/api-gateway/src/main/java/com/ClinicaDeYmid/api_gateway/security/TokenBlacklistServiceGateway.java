package com.ClinicaDeYmid.api_gateway.security;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Slf4j
@Service
public class TokenBlacklistServiceGateway {

    private final RedisTemplate<String, String> redisTemplate;

    public TokenBlacklistServiceGateway(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public Mono<Boolean> isTokenBlacklisted(String token) {
        return Mono.fromCallable(() -> {
            String hashed = DigestUtils.sha256Hex(token);
            return Boolean.TRUE.equals(redisTemplate.hasKey(hashed));
        }).onErrorResume(e -> {
            log.warn("Redis unavailable checking token blacklist, failing open: {}", e.getMessage());
            return Mono.just(false);
        });
    }
}
