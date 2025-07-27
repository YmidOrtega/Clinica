package com.ClinicaDeYmid.api_gateway.security;

import com.ClinicaDeYmid.api_gateway.dto.PublicKeyResponse;
import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

@Service
public class JwtValidatorService {

    private static final Logger logger = Logger.getLogger(JwtValidatorService.class.getName());
    private static final String ISSUER = "ClinicaDeYmid";

    @Value("${jwt.secret:}")
    private String hmacSecret;

    @Value("${jwt.algorithm:RS256}")
    private String algorithmType;

    @Value("${auth-service.url:http://auth-service}")
    private String authServiceUrl;

    private final WebClient webClient;
    private final AtomicReference<RSAPublicKey> publicKey = new AtomicReference<>(null);
    private final AtomicReference<Algorithm> algorithm = new AtomicReference<>(null);
    private volatile boolean isPublicKeyLoading = false;

    public JwtValidatorService(WebClient webClient) {
        this.webClient = webClient;
    }

    @Retry(name = "public-key-retry", fallbackMethod = "fallbackEnsurePublicKeyLoaded")
    public Mono<Void> ensurePublicKeyLoaded() {
        if (publicKey.get() != null) return Mono.empty();

        synchronized (this) {
            if (publicKey.get() != null) return Mono.empty();
            if (isPublicKeyLoading) {
                return Mono.error(new IllegalStateException("La clave pública se está cargando, intenta nuevamente."));
            }
            isPublicKeyLoading = true;
        }

        String publicKeyEndpoint = "/api/v1/auth/public-key";
        logger.info("Intentando obtener clave pública de: " + authServiceUrl + publicKeyEndpoint);

        return webClient.get()
                .uri(authServiceUrl + publicKeyEndpoint)
                .retrieve()
                .bodyToMono(PublicKeyResponse.class)
                .doOnNext(response -> {
                    try {
                        String cleanKeyContent = response.publicKey()
                                .replaceAll("-----BEGIN PUBLIC KEY-----", "")
                                .replaceAll("-----END PUBLIC KEY-----", "")
                                .replaceAll("\\s", "");
                        byte[] publicKeyBytes = Base64.getDecoder().decode(cleanKeyContent);
                        X509EncodedKeySpec publicKeySpec = new X509EncodedKeySpec(publicKeyBytes);
                        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
                        RSAPublicKey key = (RSAPublicKey) keyFactory.generatePublic(publicKeySpec);
                        publicKey.set(key);
                        algorithm.set(Algorithm.RSA256(key, null));
                        logger.info("Clave pública RSA obtenida y procesada del auth-service.");
                    } catch (Exception e) {
                        logger.severe("Error procesando clave pública obtenida del auth-service: " + e.getMessage());
                        throw new RuntimeException("Error procesando clave pública del auth-service", e);
                    }
                })
                .doOnError(e -> logger.severe("Error al obtener la clave pública del auth-service: " + e.getMessage()))
                .then()
                .doFinally(signal -> isPublicKeyLoading = false);
    }

    public Mono<Void> fallbackEnsurePublicKeyLoaded(Throwable e) {
        logger.severe("No se pudo obtener la clave pública después de los reintentos: " + e.getMessage());
        publicKey.set(null);
        algorithm.set(null);
        return Mono.error(new RuntimeException("No se pudo obtener la clave pública del auth-service.", e));
    }

    public Mono<DecodedJWT> validateAndDecodeToken(String token) {
        if ("RS256".equalsIgnoreCase(algorithmType)) {
            return ensurePublicKeyLoaded()
                    .then(Mono.defer(() -> {
                        if (algorithm.get() == null) {
                            logger.severe("No se pudo inicializar el algoritmo de firma para validar el JWT.");
                            return Mono.error(new RuntimeException("No se pudo inicializar el algoritmo para validar el JWT. ¿El Auth-Service está disponible?"));
                        }
                        try {
                            logger.info("Validando token...");
                            DecodedJWT jwt = JWT.require(algorithm.get())
                                    .withIssuer(ISSUER)
                                    .acceptLeeway(10)
                                    .build()
                                    .verify(token);
                            logger.info("Token válido. Issued at: " + jwt.getIssuedAt() + ", Exp: " + jwt.getExpiresAt());
                            return Mono.just(jwt);
                        } catch (JWTVerificationException e) {
                            logger.warning("Token inválido o expirado: " + e.getMessage());
                            return Mono.error(new RuntimeException("Token inválido o expirado", e));
                        }
                    }));
        } else {
            if (algorithm.get() == null) {
                if (hmacSecret == null || hmacSecret.trim().isEmpty() || hmacSecret.length() < 32) {
                    return Mono.error(new IllegalStateException("JWT secret inválido para HS256"));
                }
                algorithm.set(Algorithm.HMAC256(hmacSecret));
            }
            try {
                logger.info("Validando token...");
                DecodedJWT jwt = JWT.require(algorithm.get())
                        .withIssuer(ISSUER)
                        .acceptLeeway(10)
                        .build()
                        .verify(token);
                logger.info("Token válido. Issued at: " + jwt.getIssuedAt() + ", Exp: " + jwt.getExpiresAt());
                return Mono.just(jwt);
            } catch (JWTVerificationException e) {
                logger.warning("Token inválido o expirado: " + e.getMessage());
                return Mono.error(new RuntimeException("Token inválido o expirado", e));
            }
        }
    }

    public Mono<String> getSubject(String token) {
        return validateAndDecodeToken(token)
                .map(DecodedJWT::getSubject);
    }

    public Mono<String> getEmail(String token) {
        return validateAndDecodeToken(token)
                .map(jwt -> jwt.getClaim("email").asString());
    }
}
