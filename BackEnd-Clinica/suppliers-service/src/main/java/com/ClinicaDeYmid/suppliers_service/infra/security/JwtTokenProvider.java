package com.ClinicaDeYmid.suppliers_service.infra.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Component
@Slf4j
public class JwtTokenProvider {

    private final SecretKey secretKey;
    private final String issuer;

    public JwtTokenProvider(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.issuer}") String issuer) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.issuer = issuer;
    }

    /**
     * Valida el token JWT
     */
    public boolean validateToken(String token) {
        try {
            Jwts.parser()
                    .verifyWith(secretKey)
                    .requireIssuer(issuer)
                    .build()
                    .parseSignedClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            log.error("Invalid JWT token: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Extrae el userId del token
     */
    public Long getUserIdFromToken(String token) {
        Claims claims = getClaims(token);
        return claims.get("userId", Long.class);
    }

    /**
     * Extrae el username del token
     */
    public String getUsernameFromToken(String token) {
        Claims claims = getClaims(token);
        return claims.getSubject();
    }

    /**
     * Extrae los roles del token
     */
    @SuppressWarnings("unchecked")
    public List<String> getRolesFromToken(String token) {
        Claims claims = getClaims(token);
        return claims.get("roles", List.class);
    }

    /**
     * Obtiene los claims del token
     */
    private Claims getClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .requireIssuer(issuer)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}