package com.ClinicaDeYmid.auth_service.module.auth.service;

import com.ClinicaDeYmid.auth_service.module.auth.dto.TokenPair;
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
import java.nio.file.Files;
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

    @Value("${jwt.access-token.expiration:900}")
    private Long accessTokenExpiration;

    @Value("${jwt.refresh-token.expiration:604800}")
    private Long refreshTokenExpiration;

    // Fallback para HS256 si no se configuran las claves RSA
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
        // Cargar clave privada
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

        // Cargar clave pública
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

    public String generateAccessToken(User user) {
        validateUser(user);
        try {
            return JWT.create()
                    .withIssuer(ISSUER)
                    .withSubject(user.getUuid())
                    .withIssuedAt(new Date())
                    .withExpiresAt(Date.from(getAccessTokenExpirationTime()))
                    .withJWTId(UUID.randomUUID().toString())
                    .withClaim("email", user.getEmail())
                    .withClaim("type", "access")
                    .sign(algorithm);
        } catch (JWTCreationException e) {
            throw new RuntimeException("Error al generar el access token", e);
        }
    }

    public String generateRefreshToken(User user) {
        validateUser(user);
        try {
            return JWT.create()
                    .withIssuer(ISSUER)
                    .withSubject(user.getUuid())
                    .withIssuedAt(new Date())
                    .withExpiresAt(Date.from(getRefreshTokenExpirationTime()))
                    .withJWTId(UUID.randomUUID().toString())
                    .withClaim("type", "refresh")
                    .sign(algorithm);
        } catch (JWTCreationException e) {
            throw new RuntimeException("Error al generar el refresh token", e);
        }
    }

    public TokenPair generateTokenPair(User user) {
        String accessToken = generateAccessToken(user);
        String refreshToken = generateRefreshToken(user);
        return new TokenPair(accessToken, refreshToken);
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
                    .acceptLeeway(10) // 10 segundos de margen para clock skew
                    .build()
                    .verify(token);
        } catch (JWTVerificationException e) {
            throw new RuntimeException("Token inválido o expirado: " + e.getMessage(), e);
        }
    }

    public void validateAccessToken(String token) {
        DecodedJWT decodedJWT = validateAndDecodeToken(token);
        String tokenType = decodedJWT.getClaim("type").asString();
        if (!"access".equals(tokenType)) {
            throw new RuntimeException("Token no es de tipo access");
        }
    }

    public void validateRefreshToken(String token) {
        DecodedJWT decodedJWT = validateAndDecodeToken(token);
        String tokenType = decodedJWT.getClaim("type").asString();
        if (!"refresh".equals(tokenType)) {
            throw new RuntimeException("Token no es de tipo refresh");
        }
    }

    public TokenPair refreshTokens(String refreshToken, User user) {

        validateRefreshToken(refreshToken);

        // Verificar que el token pertenece al usuario
        DecodedJWT decodedJWT = validateAndDecodeToken(refreshToken);
        if (!user.getUuid().equals(decodedJWT.getSubject())) {
            throw new RuntimeException("Refresh token no pertenece al usuario");
        }

        // Generar nuevo par de tokens
        return generateTokenPair(user);
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

    private Instant getAccessTokenExpirationTime() {
        return Instant.now().plusSeconds(accessTokenExpiration);
    }

    private Instant getRefreshTokenExpirationTime() {
        return Instant.now().plusSeconds(refreshTokenExpiration);
    }

    public Long getAccessTokenExpirationSeconds() {
        return accessTokenExpiration;
    }

    public Long getRefreshTokenExpirationSeconds() {
        return refreshTokenExpiration;
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