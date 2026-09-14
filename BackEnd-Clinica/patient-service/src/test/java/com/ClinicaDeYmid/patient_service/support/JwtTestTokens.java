package com.ClinicaDeYmid.patient_service.support;

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
import java.util.Map;
import java.util.UUID;

public final class JwtTestTokens {

    private static final KeyPair KEYS = generate();

    public static final Map<String, String> USERS = Map.of(
            "SUPER_ADMIN", "00000000-0000-4000-8000-000000000001",
            "ADMIN", "00000000-0000-4000-8000-000000000002",
            "DOCTOR", "00000000-0000-4000-8000-000000000003",
            "NURSE", "00000000-0000-4000-8000-000000000004",
            "RECEPTIONIST", "00000000-0000-4000-8000-000000000005",
            "MEDICAL_RECORDS", "00000000-0000-4000-8000-000000000006");

    private JwtTestTokens() {
    }

    public static String publicKeyBase64() {
        return Base64.getEncoder().encodeToString(KEYS.getPublic().getEncoded());
    }

    public static String bearer(String role) {
        Instant now = Instant.now();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer("ClinicaDeYmid")
                .subject(USERS.get(role))
                .jwtID(UUID.randomUUID().toString())
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plus(Duration.ofMinutes(15))))
                .claim("user_id", 1L)
                .claim("email", role.toLowerCase() + "@clinica.test")
                .claim("type", "access")
                .claim("role", role)
                .build();
        try {
            SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims);
            jwt.sign(new RSASSASigner((RSAPrivateKey) KEYS.getPrivate()));
            return "Bearer " + jwt.serialize();
        } catch (JOSEException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static KeyPair generate() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
