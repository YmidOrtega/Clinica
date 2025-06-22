package com.ClinicaDeYmid.auth_service.module.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record PublicKeyResponse(
        @JsonProperty("publicKey")
        String publicKey,

        @JsonProperty("algorithm")
        String algorithm,

        @JsonProperty("keyType")
        String keyType
) {
}
