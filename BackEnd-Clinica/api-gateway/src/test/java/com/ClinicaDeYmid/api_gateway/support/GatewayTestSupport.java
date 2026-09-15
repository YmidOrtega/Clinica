package com.ClinicaDeYmid.api_gateway.support;

import com.ClinicaDeYmid.commons.openbao.testing.OpenBaoTestContainer;
import com.github.tomakehurst.wiremock.WireMockServer;
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
import com.redis.testcontainers.RedisContainer;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

public final class GatewayTestSupport {

    public static final String ISSUER = "http://auth.clinica.test";
    public static final String FRONTEND = "http://localhost:4321";
    public static final String CLIENT_ASSERTION_KEY = OpenBaoTestContainer.ensureKey("api-gateway-client", "ecdsa-p256");
    public static final UUID DOCTOR = UUID.fromString("5b1c2d3e-4f5a-4b6c-8d7e-9f0a1b2c3d4e");
    public static final RedisContainer REDIS = new RedisContainer(DockerImageName.parse("redis:7.4-alpine"));
    public static final WireMockServer AUTH = new WireMockServer(wireMockConfig().dynamicPort());
    public static final WireMockServer SERVICES = new WireMockServer(wireMockConfig().dynamicPort());

    private static final ECKey SIGNING_KEY = generate();

    static {
        REDIS.start();
        AUTH.start();
        SERVICES.start();
    }

    private GatewayTestSupport() {
    }

    public static void register(DynamicPropertyRegistry registry) {
        OpenBaoTestContainer.register(registry);
        registry.add("spring.data.redis.host", REDIS::getRedisHost);
        registry.add("spring.data.redis.port", REDIS::getRedisPort);
        registry.add("eureka.client.enabled", () -> false);
        registry.add("clinica.gateway.auth.public-url", () -> ISSUER);
        registry.add("clinica.gateway.auth.internal-url", AUTH::baseUrl);
        registry.add("clinica.gateway.auth.assertion-key", () -> CLIENT_ASSERTION_KEY);
        registry.add("clinica.gateway.frontend.origins", () -> FRONTEND);
        registry.add("clinica.gateway.frontend.home-url", () -> FRONTEND + "/");
        registry.add("clinica.gateway.routes.auth-service", AUTH::baseUrl);
        registry.add("clinica.gateway.routes.patient-service", SERVICES::baseUrl);
        registry.add("clinica.gateway.routes.clinical-history-service", SERVICES::baseUrl);
    }

    public static void publishSigningKey() {
        AUTH.stubFor(get("/oauth2/jwks").willReturn(okJson(new JWKSet(SIGNING_KEY.toPublicJWK()).toString())));
    }

    public static String tokenResponse(String nonce, String accessToken, String refreshToken, long expiresIn) {
        return """
                {"access_token": "%s", "token_type": "Bearer", "expires_in": %d, "refresh_token": "%s", "scope": "openid profile", "id_token": "%s"}"""
                .formatted(accessToken, expiresIn, refreshToken, idToken(nonce));
    }

    public static String refreshResponse(String accessToken, String refreshToken) {
        return """
                {"access_token": "%s", "token_type": "Bearer", "expires_in": 300, "refresh_token": "%s", "scope": "openid profile"}"""
                .formatted(accessToken, refreshToken);
    }

    private static String idToken(String nonce) {
        Instant now = Instant.now();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(ISSUER)
                .subject(DOCTOR.toString())
                .audience("api-gateway")
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plus(Duration.ofMinutes(30))))
                .claim("nonce", nonce)
                .claim("azp", "api-gateway")
                .claim("email", "medica@clinica.test")
                .claim("name", "Ana Rojas")
                .claim("role", "DOCTOR")
                .claim("auth_time", now.getEpochSecond())
                .claim("amr", List.of("pwd", "otp", "mfa"))
                .build();
        try {
            SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.ES256).type(JOSEObjectType.JWT).keyID(SIGNING_KEY.getKeyID()).build(), claims);
            jwt.sign(new ECDSASigner(SIGNING_KEY));
            return jwt.serialize();
        } catch (JOSEException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static ECKey generate() {
        try {
            return new ECKeyGenerator(Curve.P_256).keyID("auth-jwt-v1").generate();
        } catch (JOSEException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
