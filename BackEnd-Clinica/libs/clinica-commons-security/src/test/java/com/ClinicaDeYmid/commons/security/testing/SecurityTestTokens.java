package com.ClinicaDeYmid.commons.security.testing;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.test.context.DynamicPropertyRegistry;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class SecurityTestTokens {

    public static final String ISSUER = "http://auth.clinica.test";
    public static final String AUDIENCE = "clinica-api";

    private static final ECKey TRUSTED = generate("auth-jwt-v1");
    private static final ECKey UNTRUSTED = generate("auth-jwt-v1");

    private SecurityTestTokens() {
    }

    public static String jwkSet() {
        return new JWKSet(TRUSTED.toPublicJWK()).toString();
    }

    public static void register(DynamicPropertyRegistry registry, String... ownAudiences) {
        List<String> audiences = new ArrayList<>(List.of(AUDIENCE));
        audiences.addAll(Arrays.asList(ownAudiences));
        registry.add("clinica.security.jwt.issuer", () -> ISSUER);
        registry.add("clinica.security.jwt.jwk-set", SecurityTestTokens::jwkSet);
        registry.add("clinica.security.jwt.audiences", () -> String.join(",", audiences));
        registry.add("clinica.security.revocation.enabled", () -> false);
    }

    public static Token staff(String role, UUID user) {
        Instant now = Instant.now();
        return new Token(new JWTClaimsSet.Builder()
                .subject(user.toString())
                .claim("email", role.toLowerCase() + "@clinica.test")
                .claim("name", "Prueba " + role)
                .claim("role", role)
                .claim("auth_time", now.getEpochSecond())
                .claim("amr", List.of("pwd", "otp", "mfa"))
                .claim("acr", "urn:clinica:acr:mfa"), now);
    }

    public static Token service(String clientId, String... scopes) {
        return new Token(new JWTClaimsSet.Builder()
                .subject(clientId)
                .claim("client_id", clientId)
                .claim("scope", List.of(scopes)), Instant.now());
    }

    public static final class Token {

        private final JWTClaimsSet.Builder claims;
        private ECKey key = TRUSTED;

        private Token(JWTClaimsSet.Builder claims, Instant now) {
            this.claims = claims
                    .issuer(ISSUER)
                    .audience(AUDIENCE)
                    .jwtID(UUID.randomUUID().toString())
                    .issueTime(Date.from(now))
                    .notBeforeTime(Date.from(now))
                    .expirationTime(Date.from(now.plus(Duration.ofMinutes(5))));
        }

        public Token authenticatedAt(Instant authenticatedAt) {
            claims.claim("auth_time", authenticatedAt.getEpochSecond());
            return this;
        }

        public Token issuedAt(Instant issuedAt) {
            claims.issueTime(Date.from(issuedAt)).notBeforeTime(Date.from(issuedAt));
            return this;
        }

        public Token expiresAt(Instant expiresAt) {
            claims.expirationTime(Date.from(expiresAt));
            return this;
        }

        public Token audience(String... audiences) {
            claims.audience(List.of(audiences));
            return this;
        }

        public Token issuer(String issuer) {
            claims.issuer(issuer);
            return this;
        }

        public Token withoutSecondFactor() {
            claims.claim("amr", List.of("pwd")).claim("acr", null);
            return this;
        }

        public Token actingThrough(String clientId) {
            claims.claim("client_id", clientId).claim("act", Map.of("sub", clientId, "iss", ISSUER));
            return this;
        }

        public Token claim(String name, Object value) {
            claims.claim(name, value);
            return this;
        }

        public Token signedWithUntrustedKey() {
            key = UNTRUSTED;
            return this;
        }

        public String value() {
            try {
                SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.ES256).type(JOSEObjectType.JWT).keyID(key.getKeyID()).build(),
                        claims.build());
                jwt.sign(new ECDSASigner(key));
                return jwt.serialize();
            } catch (JOSEException ex) {
                throw new IllegalStateException(ex);
            }
        }

        public String bearer() {
            return "Bearer " + value();
        }
    }

    private static ECKey generate(String keyId) {
        try {
            return new ECKeyGenerator(Curve.P_256).keyID(keyId).generate();
        } catch (JOSEException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
