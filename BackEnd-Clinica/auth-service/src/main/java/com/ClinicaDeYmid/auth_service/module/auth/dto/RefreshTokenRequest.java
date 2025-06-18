package com.ClinicaDeYmid.auth_service.module.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

public record RefreshTokenRequest(
        @NotBlank(message = "El refresh token es requerido")
        @JsonProperty("refresh_token")
        String refreshToken
) {}
