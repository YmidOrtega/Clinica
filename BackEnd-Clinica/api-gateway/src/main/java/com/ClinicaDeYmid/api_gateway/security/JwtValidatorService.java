package com.ClinicaDeYmid.api_gateway.security;


import com.ClinicaDeYmid.api_gateway.dto.PublicKeyResponse;
import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.time.Duration;
import java.util.Base64;
import java.util.Date;
import java.util.logging.Logger;

@Service
public class JwtValidatorService {

    private static final Logger logger = Logger.getLogger(JwtValidatorService.class.getName());

    private static final String ISSUER = "ClinicaDeYmid"; //

    @Value("${jwt.secret:}")
    private String hmacSecret;

    @Value("${jwt.algorithm:RS256}") //
    private String algorithmType;

    @Value("${auth-service.url:http://auth-service}")
    private String authServiceUrl;

    private final WebClient.Builder webClientBuilder;

    private RSAPublicKey publicKey;
    private Algorithm algorithm;
    private WebClient authWebClient;

    public JwtValidatorService(WebClient.Builder webClientBuilder) { //
        this.webClientBuilder = webClientBuilder;
    }

    @PostConstruct
    public void init() {
        try {

            this.authWebClient = webClientBuilder
                    .clientConnector(new ReactorClientHttpConnector(
                            reactor.netty.http.client.HttpClient.create().responseTimeout(Duration.ofSeconds(5))
                    ))
                    .baseUrl(authServiceUrl)
                    .build();

            if ("RS256".equalsIgnoreCase(algorithmType)) {

                loadRSAPublicKeyFromAuthService()
                        .doOnError(e -> logger.severe("Error fatal al obtener la clave pública: " + e.getMessage()))
                        .block();

                if (publicKey == null) {
                    throw new IllegalStateException("No se pudo obtener la clave pública del auth-service.");
                }
                algorithm = Algorithm.RSA256(publicKey, null);
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
            logger.info("JwtValidatorService inicializado con algoritmo: " + algorithmType);
        } catch (Exception e) {
            logger.severe("Error inicializando JwtValidatorService: " + e.getMessage());
            throw new RuntimeException("Error inicializando JwtValidatorService", e);
        }
    }

    private Mono<Void> loadRSAPublicKeyFromAuthService() {
        String publicKeyEndpoint = "/api/v1/auth/public-key";
        logger.info("Intentando obtener clave pública de: " + authServiceUrl + publicKeyEndpoint);

        return authWebClient.get()
                .uri(publicKeyEndpoint)
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
                        this.publicKey = (RSAPublicKey) keyFactory.generatePublic(publicKeySpec);
                        logger.info("Clave pública RSA obtenida y procesada del auth-service.");
                    } catch (Exception e) {
                        logger.severe("Error procesando clave pública obtenida del auth-service: " + e.getMessage());
                        throw new RuntimeException("Error procesando clave pública del auth-service", e);
                    }
                })
                .then()
                .doOnError(e -> logger.severe("Error al obtener la clave pública del auth-service: " + e.getMessage()));
    }


    public DecodedJWT validateAndDecodeToken(String token) {
        try {
            logger.info("Validando token...");
            DecodedJWT jwt = JWT.require(algorithm)
                    .withIssuer(ISSUER)
                    .acceptLeeway(10)
                    .build()
                    .verify(token);

            logger.info("Token válido. Issued at: " + jwt.getIssuedAt() + ", Exp: " + jwt.getExpiresAt());
            return jwt;

        } catch (JWTVerificationException e) {
            logger.warning("Token inválido o expirado: " + e.getMessage());
            throw new RuntimeException("Token inválido o expirado", e);
        }
    }

    public String getSubject(String token) {
        return validateAndDecodeToken(token).getSubject();
    }

    public String getEmail(String token) {
        return validateAndDecodeToken(token).getClaim("email").asString();
    }
}