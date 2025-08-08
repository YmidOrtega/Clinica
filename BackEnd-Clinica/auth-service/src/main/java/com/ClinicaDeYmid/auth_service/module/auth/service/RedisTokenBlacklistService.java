package com.ClinicaDeYmid.auth_service.module.auth.service;

import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
public class RedisTokenBlacklistService implements TokenBlacklistService {

    private final RedisTemplate<String, String> redisTemplate;

    public RedisTokenBlacklistService(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void blacklistToken(String token, long expirationInSeconds) {
        String hashed = DigestUtils.sha256Hex(token);
        redisTemplate.opsForValue().set(hashed, "revoked", expirationInSeconds, TimeUnit.SECONDS);
    }

    @Override
    public boolean isTokenBlacklisted(String token) {
        String hashed = DigestUtils.sha256Hex(token);
        return redisTemplate.hasKey(hashed);
    }
}
