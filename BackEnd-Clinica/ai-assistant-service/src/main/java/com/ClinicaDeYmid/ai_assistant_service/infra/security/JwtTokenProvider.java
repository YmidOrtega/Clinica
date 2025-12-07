package com.ClinicaDeYmid.ai_assistant_service.infra.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Collections;
import java.util.List;

@Component
@Slf4j
public class JwtTokenProvider {

    private static final String ISSUER = "ClinicaDeYmid";
    private static final String ALGORITHM_TYPE = "RSA";

    @Value("${jwt.rsa.public-key-path:}")
    private String publicKeyPath;

    @Value("${jwt.rsa.public-key:}")
    private String publicKeyContent;

    @Value("${jwt.secret:}")
    private String hmacSecret;

    @Value("${jwt.algorithm:RS256}")
    private String algorithmType;

    private final ResourceLoader resourceLoader;

    private RSAPublicKey publicKey;
    private SecretKey secretKey;

    public JwtTokenProvider(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    @PostConstruct
    public void init() {
        try {
            if ("RS256".equalsIgnoreCase(algorithmType)) {
                loadRSAPublicKey();
                log.info("JWT TokenProvider inicializado con algoritmo RS256");
            } else if ("HS256".equalsIgnoreCase(algorithmType)) {
                if (hmacSecret == null || hmacSecret.trim().isEmpty()) {
                    throw new IllegalStateException("JWT secret es requerido para algoritmo HS256");
                }
                if (hmacSecret.length() < 32) {
                    throw new IllegalStateException("JWT secret debe tener al menos 32 caracteres para HS256");
                }
                secretKey = Keys.hmacShaKeyFor(hmacSecret.getBytes(StandardCharsets.UTF_8));
                log.info("JWT TokenProvider inicializado con algoritmo HS256");
            } else {
                throw new IllegalStateException("Algoritmo no soportado: " + algorithmType + ". Use RS256 o HS256");
            }
        } catch (Exception e) {
            log.error("Error inicializando JwtTokenProvider", e);
            throw new RuntimeException("Error inicializando JwtTokenProvider", e);
        }
    }

    private void loadRSAPublicKey() throws IOException, NoSuchAlgorithmException, InvalidKeySpecException {
        String keyContent;
        
        // Prioridad 1: Variable de entorno con el contenido de la clave
        if (publicKeyContent != null && !publicKeyContent.trim().isEmpty()) {
            keyContent = publicKeyContent;
            log.info("Clave pública RSA cargada desde variable de entorno");
        }
        // Prioridad 2: Archivo en el classpath
        else if (publicKeyPath != null && !publicKeyPath.trim().isEmpty()) {
            Resource publicKeyResource = resourceLoader.getResource(publicKeyPath);
            if (!publicKeyResource.exists()) {
                throw new IllegalStateException("Archivo de clave pública no encontrado: " + publicKeyPath);
            }
            keyContent = new String(publicKeyResource.getInputStream().readAllBytes());
            log.info("Clave pública RSA cargada desde archivo: {}", publicKeyPath);
        }
        else {
            throw new IllegalStateException("No se ha configurado jwt.rsa.public-key ni jwt.rsa.public-key-path");
        }

        // Limpiar el contenido de la clave
        String cleanedKeyContent = keyContent
                .replaceAll("-----BEGIN PUBLIC KEY-----", "")
                .replaceAll("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s", "");

        byte[] publicKeyBytes = Base64.getDecoder().decode(cleanedKeyContent);
        X509EncodedKeySpec publicKeySpec = new X509EncodedKeySpec(publicKeyBytes);
        KeyFactory keyFactory = KeyFactory.getInstance(ALGORITHM_TYPE);
        this.publicKey = (RSAPublicKey) keyFactory.generatePublic(publicKeySpec);
    }

    public boolean validateToken(String token) {
        try {
            if ("RS256".equalsIgnoreCase(algorithmType)) {
                Jwts.parser()
                        .verifyWith(publicKey)
                        .requireIssuer(ISSUER)
                        .build()
                        .parseSignedClaims(token);
            } else {
                Jwts.parser()
                        .verifyWith(secretKey)
                        .requireIssuer(ISSUER)
                        .build()
                        .parseSignedClaims(token);
            }
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            log.error("Token JWT inválido: {}", e.getMessage());
            return false;
        }
    }

    public String getUuidFromToken(String token) {
        try {
            Claims claims = getClaims(token);
            return claims.getSubject();
        } catch (Exception e) {
            log.error("Error al extraer UUID del token", e);
            return null;
        }
    }

    public String getEmailFromToken(String token) {
        try {
            Claims claims = getClaims(token);
            return claims.get("email", String.class);
        } catch (Exception e) {
            log.error("Error al extraer email del token", e);
            return null;
        }
    }

    public String getRoleFromToken(String token) {
        try {
            Claims claims = getClaims(token);
            return claims.get("role", String.class);
        } catch (Exception e) {
            log.error("Error al extraer rol del token", e);
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    public List<String> getPermissionsFromToken(String token) {
        try {
            Claims claims = getClaims(token);
            List<String> permissions = claims.get("permissions", List.class);
            return permissions != null ? permissions : Collections.emptyList();
        } catch (Exception e) {
            log.error("Error al extraer permisos del token", e);
            return Collections.emptyList();
        }
    }

    public boolean isAccessToken(String token) {
        try {
            Claims claims = getClaims(token);
            String type = claims.get("type", String.class);
            return "access".equals(type);
        } catch (Exception e) {
            log.error("Error al verificar tipo de token", e);
            return false;
        }
    }

    private Claims getClaims(String token) {
        if ("RS256".equalsIgnoreCase(algorithmType)) {
            return Jwts.parser()
                    .verifyWith(publicKey)
                    .requireIssuer(ISSUER)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } else {
            return Jwts.parser()
                    .verifyWith(secretKey)
                    .requireIssuer(ISSUER)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        }
    }
}