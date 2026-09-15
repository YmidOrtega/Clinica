package com.ClinicaDeYmid.commons.security;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class DelegatedTokens {

    static final String TOKEN_EXCHANGE = "urn:ietf:params:oauth:grant-type:token-exchange";
    static final String ACCESS_TOKEN_TYPE = "urn:ietf:params:oauth:token-type:access_token";
    private static final String JWT_BEARER_ASSERTION = "urn:ietf:params:oauth:client-assertion-type:jwt-bearer";
    private static final int MAX_CACHED = 10_000;

    private record Issued(String value, Instant renewAt) {
    }

    private final RestClient http;
    private final ClinicaSecurityProperties.Client client;
    private final String issuer;
    private final ClientAssertionSigner signer;
    private final Clock clock;
    private final Map<String, Issued> exchanged = new ConcurrentHashMap<>();
    private volatile Issued serviceToken;

    public DelegatedTokens(RestClient.Builder http, ClinicaSecurityProperties.Client client, String issuer, ClientAssertionSigner signer, Clock clock) {
        this.http = http.build();
        this.client = client;
        this.issuer = issuer;
        this.signer = signer;
        this.clock = clock;
    }

    public String serviceToken() {
        Issued current = serviceToken;
        if (current == null || !Instant.now(clock).isBefore(current.renewAt())) {
            current = request(Map.of("grant_type", "client_credentials"));
            serviceToken = current;
        }
        return current.value();
    }

    public String forAudience(String subjectToken, String audience) {
        String key = sha256(subjectToken) + "|" + audience;
        Issued cached = exchanged.get(key);
        if (cached != null && Instant.now(clock).isBefore(cached.renewAt())) {
            return cached.value();
        }
        Issued issued = request(Map.of("grant_type", TOKEN_EXCHANGE, "subject_token", subjectToken, "subject_token_type", ACCESS_TOKEN_TYPE,
                "actor_token", serviceToken(), "actor_token_type", ACCESS_TOKEN_TYPE, "audience", audience));
        if (exchanged.size() >= MAX_CACHED) {
            Instant now = Instant.now(clock);
            exchanged.values().removeIf(entry -> !now.isBefore(entry.renewAt()));
        }
        exchanged.put(key, issued);
        return issued.value();
    }

    private Issued request(Map<String, String> grant) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        grant.forEach(form::add);
        form.add("client_id", client.id());
        form.add("client_assertion_type", JWT_BEARER_ASSERTION);
        try {
            form.add("client_assertion", signer.assertion(client.id(), issuer));
            JsonNode response = http.post()
                    .uri(client.tokenUri())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(JsonNode.class);
            if (response == null || !response.hasNonNull("access_token")) {
                throw new DelegationUnavailableException("auth-service returned no access token", null);
            }
            Duration lifetime = Duration.ofSeconds(response.path("expires_in").asLong(0));
            Duration margin = lifetime.compareTo(client.renewBeforeExpiry().multipliedBy(2)) > 0 ? client.renewBeforeExpiry() : lifetime.dividedBy(2);
            return new Issued(response.get("access_token").asText(), Instant.now(clock).plus(lifetime).minus(margin));
        } catch (RestClientException | IllegalStateException failure) {
            throw new DelegationUnavailableException("Could not obtain a token from auth-service for " + grant.get("grant_type"), failure);
        }
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
