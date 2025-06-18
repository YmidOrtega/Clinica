package com.ClinicaDeYmid.auth_service.module.auth.service;

import com.ClinicaDeYmid.auth_service.module.user.entity.User;
import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTCreationException;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;

@Service
public class TokenService {

    private static final String ISSUER = "ClinicaDeYmid";
    private static final String ALGORITHM_TYPE = "RSA";

    @Value("${jwt.rsa.private-key-path:classpath:keys/private_key.pem}")
    private String privateKeyPath;

    @Value("${jwt.rsa.public-key-path:classpath:keys/public_key.pem}")
    private String publicKeyPath;

    @Value("${jwt.expiration:3600}")
    private Long expiration;

    @Value("${jwt.secret:}")
    private String hmacSecret;

    @Value("${jwt.algorithm:RS256}")
    private String algorithmType;

    private final ResourceLoader resourceLoader;

    private RSAPrivateKey privateKey;
    private RSAPublicKey publicKey;
    private Algorithm algorithm;

    public TokenService(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    @PostConstruct
    public void init() {
        try {
            if ("RS256".equalsIgnoreCase(algorithmType)) {
                loadRSAKeys();
                algorithm = Algorithm.RSA256(publicKey, privateKey);
            } else if ("HS256".equalsIgnoreCase(algorithmType)) {
                if (hmacSecret == null || hmacSecret.trim().isEmpty()) {
                    throw new IllegalStateException("JWT secret is required for HS256 algorithm");
                }
                if (hmacSecret.length() < 32) {
                    throw new IllegalStateException("JWT secret debe tener al menos 32 caracteres para HS256");
                }
                algorithm = Algorithm.HMAC256(hmacSecret);
            } else {
                throw new IllegalStateException("Algoritmo no soportado: " + algorithmType + ". Use RS256 o HS256");
            }
        } catch (Exception e) {
            throw new RuntimeException("Error inicializando TokenService", e);
        }
    }

    private void loadRSAKeys() throws IOException, NoSuchAlgorithmException, InvalidKeySpecException {

        Resource privateKeyResource = resourceLoader.getResource(privateKeyPath);
        if (!privateKeyResource.exists()) {
            throw new IllegalStateException("Archivo de clave privada no encontrado: " + privateKeyPath);
        }

        String privateKeyContent = new String(privateKeyResource.getInputStream().readAllBytes())
                .replaceAll("-----BEGIN PRIVATE KEY-----", "")
                .replaceAll("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");

        byte[] privateKeyBytes = Base64.getDecoder().decode(privateKeyContent);
        PKCS8EncodedKeySpec privateKeySpec = new PKCS8EncodedKeySpec(privateKeyBytes);
        KeyFactory keyFactory = KeyFactory.getInstance(ALGORITHM_TYPE);
        this.privateKey = (RSAPrivateKey) keyFactory.generatePrivate(privateKeySpec);

        Resource publicKeyResource = resourceLoader.getResource(publicKeyPath);
        if (!publicKeyResource.exists()) {
            throw new IllegalStateException("Archivo de clave pública no encontrado: " + publicKeyPath);
        }

        String publicKeyContent = new String(publicKeyResource.getInputStream().readAllBytes())
                .replaceAll("-----BEGIN PUBLIC KEY-----", "")
                .replaceAll("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s", "");

        byte[] publicKeyBytes = Base64.getDecoder().decode(publicKeyContent);
        X509EncodedKeySpec publicKeySpec = new X509EncodedKeySpec(publicKeyBytes);
        this.publicKey = (RSAPublicKey) keyFactory.generatePublic(publicKeySpec);
    }

    public String generateToken(User user) {
        validateUser(user);
        try {
            return JWT.create()
                    .withIssuer(ISSUER)
                    .withSubject(user.getUuid())
                    .withIssuedAt(new Date())
                    .withExpiresAt(Date.from(getExpirationTime()))
                    .withJWTId(UUID.randomUUID().toString())
                    .withClaim("email", user.getEmail())
                    .sign(algorithm);
        } catch (JWTCreationException e) {
            throw new RuntimeException("Error al generar el token", e);
        }
    }

    public String getSubject(String token) {
        return validateAndDecodeToken(token).getSubject();
    }

    public String getEmail(String token) {
        return validateAndDecodeToken(token).getClaim("email").asString();
    }

    public DecodedJWT validateAndDecodeToken(String token) {
        try {
            return JWT.require(algorithm)
                    .withIssuer(ISSUER)
                    .acceptLeeway(10)
                    .build()
                    .verify(token);
        } catch (JWTVerificationException e) {
            throw new RuntimeException("Token inválido o expirado: " + e.getMessage(), e);
        }
    }

    public boolean isTokenValid(String token) {
        try {
            validateAndDecodeToken(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public String getAlgorithmType() {
        return algorithmType;
    }

    // Solo para RS256 - retorna la clave pública para verificación externa
    public RSAPublicKey getPublicKey() {
        if (!"RS256".equalsIgnoreCase(algorithmType)) {
            throw new UnsupportedOperationException("Clave pública solo disponible para algoritmo RS256");
        }
        return publicKey;
    }

    private Instant getExpirationTime() {
        return Instant.now().plusSeconds(expiration);
    }

    private void validateUser(User user) {
        if (user == null) {
            throw new IllegalArgumentException("El usuario no puede ser null");
        }
        if (user.getEmail() == null || user.getEmail().trim().isEmpty()) {
            throw new IllegalArgumentException("El usuario debe tener un email válido");
        }
        if (user.getUuid() == null || user.getUuid().trim().isEmpty()) {
            throw new IllegalArgumentException("El usuario debe tener un UUID válido");
        }
    }
}