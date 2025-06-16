package com.ClinicaDeYmid.auth_service.module.auth.service;

import com.ClinicaDeYmid.auth_service.module.user.entity.User;
import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTCreationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;


import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@Service
public class TokenService {

    private static final String ISSUER = "ClinicaDeYmid";

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration}")
    private Long expiration;

    private Algorithm getAlgorithm() {
        return Algorithm.HMAC256(secret);
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
                    .sign(getAlgorithm());
        } catch (JWTCreationException e) {
            throw new RuntimeException("Error al generar el token", e);
        }
    }

    public String getSubject(String token) {
        try {
            DecodedJWT jwt = JWT.require(getAlgorithm())
                    .withIssuer(ISSUER)
                    .acceptLeeway(10)
                    .build()
                    .verify(token);
            return jwt.getSubject();
        } catch (Exception e) {
            throw new RuntimeException("Token inválido o expirado", e);
        }
    }

    private Instant getExpirationTime() {
        return Instant.now().plusSeconds(expiration);
    }

    private void validateUser(User user) {
        if (user.getEmail() == null || user.getUuid() == null) {
            throw new IllegalArgumentException("El usuario no tiene email o uuid");
        }
    }
}

