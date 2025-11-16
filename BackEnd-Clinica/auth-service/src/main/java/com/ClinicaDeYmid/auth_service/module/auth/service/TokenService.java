package com.ClinicaDeYmid.auth_service.module.auth.service;

import com.ClinicaDeYmid.auth_service.infra.exceptions.InvalidTokenException;
import com.ClinicaDeYmid.auth_service.module.auth.dto.TokenPair;
import com.ClinicaDeYmid.auth_service.module.auth.entity.RefreshToken;
import com.ClinicaDeYmid.auth_service.module.user.entity.User;
import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTCreationException;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

@Slf4j
@Service
@RequiredArgsConstructor
public class TokenService {

    private static final String ISSUER = "ClinicaDeYmid";
    private static final String ALGORITHM_TYPE = "RSA";

    @Value("${jwt.rsa.private-key-path}")
    private String privateKeyPath;

    @Value("${jwt.rsa.public-key-path}")
    private String publicKeyPath;

    @Value("${jwt.access-token.expiration}")
    private Long accessTokenExpiration;

    @Value("${jwt.refresh-token.expiration}")
    private Long refreshTokenExpiration;

    @Value("${jwt.secret}")
    private String hmacSecret;

    @Value("${jwt.algorithm}")
    private String algorithmType;

    private final ResourceLoader resourceLoader;
    private final TokenBlacklistService tokenBlacklistService;
    private final RefreshTokenService refreshTokenService;

    private RSAPrivateKey privateKey;
    private RSAPublicKey publicKey;
    private Algorithm algorithm;

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

    private Instant getAccessTokenExpirationTime() {
        return Instant.now().plusSeconds(accessTokenExpiration);
    }

    private Instant getRefreshTokenExpirationTime() {
        return Instant.now().plusSeconds(refreshTokenExpiration);
    }

    private void validateUser(User user) {
        if (user == null || user.getUuid() == null || user.getEmail() == null) {
            throw new IllegalArgumentException("Usuario inválido para generar token");
        }
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
                    .withClaim("role", user.getRole().getName())
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

    /**
     * Genera par de tokens y persiste el refresh token
     */
    public TokenPair generateTokenPair(User user, String ipAddress, String userAgent) {
        String accessToken = generateAccessToken(user);
        String refreshToken = generateRefreshToken(user);

        // Persistir el refresh token
        refreshTokenService.createRefreshToken(refreshToken, user, ipAddress, userAgent);

        return new TokenPair(accessToken, refreshToken, "Bearer", accessTokenExpiration);
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
            log.warn("Token inválido: {}", e.getMessage());
            throw new InvalidTokenException("Token inválido o expirado");
        }
    }

    public void validateAccessToken(String token) {
        try {
            log.debug("Verificando si token está en blacklist");
            if (tokenBlacklistService.isTokenBlacklisted(token)) {
                throw new InvalidTokenException("Token ha sido revocado");
            }

            log.debug("Decodificando y validando token");
            DecodedJWT decodedJWT = validateAndDecodeToken(token);

            log.debug("Verificando tipo de token");
            String tokenType = decodedJWT.getClaim("type").asString();

            if (!"access".equals(tokenType)) {
                throw new InvalidTokenException("Se requiere un token de acceso");
            }

            log.debug("Token de acceso válido");

        } catch (InvalidTokenException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error inesperado en validateAccessToken", e);
            throw new InvalidTokenException("Error validando token: " + e.getMessage());
        }
    }

    public void validateRefreshToken(String token) {
        DecodedJWT decodedJWT = validateAndDecodeToken(token);
        String tokenType = decodedJWT.getClaim("type").asString();
        if (!"refresh".equals(tokenType)) {
            throw new RuntimeException("Token no es de tipo refresh");
        }

        // Verificar que el token esté en la base de datos y sea válido
        RefreshToken refreshToken = refreshTokenService.findValidToken(token)
                .orElseThrow(() -> new RuntimeException("Refresh token no válido o revocado"));

        if (!refreshToken.isValid()) {
            throw new RuntimeException("Refresh token expirado o revocado");
        }
    }

    /**
     * Refresca tokens con rotación automática
     */
    public TokenPair refreshTokens(String oldRefreshToken, User user, String ipAddress, String userAgent) {
        validateRefreshToken(oldRefreshToken);

        DecodedJWT decodedJWT = validateAndDecodeToken(oldRefreshToken);
        if (!user.getUuid().equals(decodedJWT.getSubject())) {
            throw new RuntimeException("Refresh token no pertenece al usuario");
        }

        // Obtener el token de la BD
        RefreshToken oldToken = refreshTokenService.findValidToken(oldRefreshToken)
                .orElseThrow(() -> new RuntimeException("Refresh token no encontrado"));

        // Generar nuevos tokens
        String newAccessToken = generateAccessToken(user);
        String newRefreshToken = generateRefreshToken(user);

        // Rotar el refresh token
        refreshTokenService.rotateToken(oldToken, newRefreshToken, ipAddress, userAgent);

        return new TokenPair(newAccessToken, newRefreshToken, "Bearer", accessTokenExpiration);
    }

    public boolean isTokenValid(String token) {
        try {
            validateAndDecodeToken(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public long getExpirationInSeconds(String token) {
        DecodedJWT decodedJWT = validateAndDecodeToken(token);
        Date expiration = decodedJWT.getExpiresAt();
        return (expiration.getTime() - System.currentTimeMillis()) / 1000;
    }

    public RSAPublicKey getPublicKey() {
        if (!"RS256".equalsIgnoreCase(algorithmType)) {
            throw new IllegalStateException("La clave pública solo está disponible con RS256");
        }
        return publicKey;
    }
}