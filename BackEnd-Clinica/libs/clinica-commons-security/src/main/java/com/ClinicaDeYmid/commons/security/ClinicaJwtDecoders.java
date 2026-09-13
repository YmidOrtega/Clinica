package com.ClinicaDeYmid.commons.security;

import org.springframework.core.io.Resource;
import org.springframework.security.converter.RsaKeyConverters;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.security.interfaces.RSAPublicKey;

public final class ClinicaJwtDecoders {

    private static final String PEM_HEADER = "-----BEGIN PUBLIC KEY-----";
    private static final String PEM_FOOTER = "-----END PUBLIC KEY-----";

    private ClinicaJwtDecoders() {
    }

    public static JwtDecoder fromProperties(ClinicaSecurityProperties.Jwt properties) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(publicKeyOf(properties))
                .signatureAlgorithm(SignatureAlgorithm.RS256)
                .build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(properties.issuer()),
                new JwtClaimValidator<String>(JwtClaimNames.SUB, subject -> subject != null && !subject.isBlank()),
                new JwtClaimValidator<String>(ClinicaJwtClaims.TYPE, ClinicaJwtClaims.ACCESS_TOKEN_TYPE::equals)));
        return decoder;
    }

    static RSAPublicKey publicKeyOf(ClinicaSecurityProperties.Jwt properties) {
        if (properties.publicKey() != null && !properties.publicKey().isBlank()) {
            return parse(new ByteArrayInputStream(normalizePem(properties.publicKey()).getBytes(StandardCharsets.US_ASCII)));
        }
        Resource location = properties.publicKeyLocation();
        if (location != null && location.exists()) {
            try (InputStream input = location.getInputStream()) {
                return parse(input);
            } catch (IOException ex) {
                throw new UncheckedIOException("Cannot read JWT public key from " + location.getDescription(), ex);
            }
        }
        throw new IllegalStateException("Configure clinica.security.jwt.public-key or clinica.security.jwt.public-key-location");
    }

    private static RSAPublicKey parse(InputStream pem) {
        return RsaKeyConverters.x509().convert(pem);
    }

    private static String normalizePem(String value) {
        String pem = value.replace("\\n", "\n").trim();
        if (pem.startsWith(PEM_HEADER)) {
            return pem;
        }
        return PEM_HEADER + "\n" + pem.replaceAll("\\s", "") + "\n" + PEM_FOOTER;
    }
}
