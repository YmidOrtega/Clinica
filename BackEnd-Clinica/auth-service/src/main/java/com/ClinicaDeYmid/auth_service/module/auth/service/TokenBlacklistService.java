package com.ClinicaDeYmid.auth_service.module.auth.service;

public interface TokenBlacklistService {
    void blacklistToken(String token, long expirationInSeconds);
    boolean isTokenBlacklisted(String token);
}
