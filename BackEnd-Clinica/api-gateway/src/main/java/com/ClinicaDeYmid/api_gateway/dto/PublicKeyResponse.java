package com.ClinicaDeYmid.api_gateway.dto;

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
