package com.ClinicaDeYmid.commons.security;

import com.ClinicaDeYmid.commons.openbao.transit.KeyVersion;
import com.ClinicaDeYmid.commons.openbao.transit.SignatureFormat;
import com.ClinicaDeYmid.commons.openbao.transit.TransitKeys;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

public class TransitClientAssertionSigner implements ClientAssertionSigner {

    private static final Duration LIFETIME = Duration.ofMinutes(1);
    private static final Base64.Encoder BASE64URL = Base64.getUrlEncoder().withoutPadding();
    private static final ObjectMapper JSON = new ObjectMapper();

    private final TransitKeys keys;
    private final Clock clock;

    public TransitClientAssertionSigner(TransitKeys keys, Clock clock) {
        this.keys = keys;
        this.clock = clock;
    }

    @Override
    public String assertion(String clientId, String audience) {
        long now = Instant.now(clock).getEpochSecond();
        String header = encode(Map.of("alg", "ES256", "typ", "JWT"));
        String payload = encode(Map.of("iss", clientId, "sub", clientId, "aud", audience, "iat", now, "exp", now + LIFETIME.toSeconds(),
                "jti", UUID.randomUUID().toString()));
        String signingInput = header + "." + payload;
        KeyVersion version = keys.current().latest();
        byte[] signature = keys.client().sign(version, signingInput.getBytes(StandardCharsets.US_ASCII), SignatureFormat.JWS);
        return signingInput + "." + BASE64URL.encodeToString(signature);
    }

    private static String encode(Map<String, Object> value) {
        try {
            return BASE64URL.encodeToString(JSON.writeValueAsBytes(value));
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
