package com.ClinicaDeYmid.auth_service.module.auth.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Service
public class RedisTokenBlacklistService implements TokenBlacklistService {

    private final RedisTemplate<String, String> redisTemplate;

    public RedisTokenBlacklistService(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void blacklistToken(String token, long expirationInSeconds) {
        redisTemplate.opsForValue().set(token, "blacklisted", Duration.ofSeconds(expirationInSeconds));
    }

    @Override
    public boolean isTokenBlacklisted(String token) {
        return redisTemplate.hasKey(token);
    }
}
