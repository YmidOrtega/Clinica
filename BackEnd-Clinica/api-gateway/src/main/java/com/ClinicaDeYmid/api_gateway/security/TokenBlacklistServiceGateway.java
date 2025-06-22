package com.ClinicaDeYmid.api_gateway.security;

import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class TokenBlacklistServiceGateway {

    private final RedisTemplate<String, String> redisTemplate;

    public TokenBlacklistServiceGateway(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public boolean isTokenBlacklisted(String token) {
        String hashed = DigestUtils.sha256Hex(token);
        return redisTemplate.hasKey(hashed);
    }
}