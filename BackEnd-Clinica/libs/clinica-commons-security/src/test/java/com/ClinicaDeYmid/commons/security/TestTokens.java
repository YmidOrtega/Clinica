package com.ClinicaDeYmid.commons.security;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;
import java.util.function.Consumer;

final class TestTokens {

    static final KeyPair TRUSTED_KEYS = generateKeyPair();
    static final KeyPair UNTRUSTED_KEYS = generateKeyPair();
    static final String USER_UUID = "7d1c3f2e-9a4b-4c1d-8e2f-0a1b2c3d4e5f";

    private TestTokens() {
    }

    static String trustedPublicKeyBase64() {
        return Base64.getEncoder().encodeToString(TRUSTED_KEYS.getPublic().getEncoded());
    }

    static String trustedPublicKeyPem() {
        String body = trustedPublicKeyBase64().replaceAll("(.{64})", "$1\\\\n");
        return "-----BEGIN PUBLIC KEY-----\\n" + body + "\\n-----END PUBLIC KEY-----";
    }

    static String accessToken(String role) {
        return token(TRUSTED_KEYS, claims -> claims.claim("role", role));
    }

    static String token(KeyPair keys, Consumer<JWTClaimsSet.Builder> customizer) {
        Instant now = Instant.now();
        JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder()
                .issuer("ClinicaDeYmid")
                .subject(USER_UUID)
                .jwtID(UUID.randomUUID().toString())
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plus(Duration.ofMinutes(15))))
                .claim("user_id", 42L)
                .claim("email", "doctor@clinica.test")
                .claim("type", "access")
                .claim("role", "DOCTOR");
        customizer.accept(claims);
        return sign(keys, claims.build());
    }

    private static String sign(KeyPair keys, JWTClaimsSet claims) {
        try {
            SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims);
            jwt.sign(new RSASSASigner((RSAPrivateKey) keys.getPrivate()));
            return jwt.serialize();
        } catch (JOSEException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static KeyPair generateKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
