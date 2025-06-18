package com.ClinicaDeYmid.auth_service.module.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;


public record TokenPair(
        @JsonProperty("access_token") String accessToken,
        @JsonProperty("refresh_token") String refreshToken,
        @JsonProperty("token_type") String tokenType,
        @JsonProperty("expires_in") Long expiresIn
) {

    public TokenPair {
        if (tokenType == null) {
            tokenType = "Bearer";
        }
        if (expiresIn == null) {
            expiresIn = 900L;
        }
    }

    // Constructor de conveniencia
    public TokenPair(String accessToken, String refreshToken) {
        this(accessToken, refreshToken, "Bearer", 900L);
    }
}