package com.ClinicaDeYmid.commons.security;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.jwk.source.JWKSourceBuilder;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import java.net.MalformedURLException;
import java.net.URI;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public final class ClinicaJwtDecoders {

    private ClinicaJwtDecoders() {
    }

    public static JwtDecoder fromProperties(ClinicaSecurityProperties.Jwt properties, List<OAuth2TokenValidator<Jwt>> extraValidators) {
        if (properties.issuer() == null || properties.issuer().isBlank()) {
            throw new IllegalStateException("Configure clinica.security.jwt.issuer with the public issuer of auth-service");
        }
        DefaultJWTProcessor<SecurityContext> processor = new DefaultJWTProcessor<>();
        processor.setJWSKeySelector(new JWSVerificationKeySelector<>(JWSAlgorithm.ES256, jwkSource(properties)));
        processor.setJWTClaimsSetVerifier((claims, context) -> {
        });
        NimbusJwtDecoder decoder = new NimbusJwtDecoder(processor);
        List<OAuth2TokenValidator<Jwt>> validators = new ArrayList<>(List.of(
                JwtValidators.createDefaultWithIssuer(properties.issuer()),
                new JwtClaimValidator<String>(JwtClaimNames.SUB, subject -> subject != null && !subject.isBlank()),
                new JwtClaimValidator<Collection<String>>(JwtClaimNames.AUD,
                        audience -> audience != null && audience.stream().anyMatch(properties.audiences()::contains))));
        validators.addAll(extraValidators);
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(validators));
        return decoder;
    }

    static JWKSource<SecurityContext> jwkSource(ClinicaSecurityProperties.Jwt properties) {
        if (properties.jwkSet() != null && !properties.jwkSet().isBlank()) {
            try {
                return new ImmutableJWKSet<>(JWKSet.parse(properties.jwkSet()));
            } catch (ParseException ex) {
                throw new IllegalStateException("clinica.security.jwt.jwk-set is not a valid JWK set", ex);
            }
        }
        if (properties.jwkSetUri() == null || properties.jwkSetUri().isBlank()) {
            throw new IllegalStateException("Configure clinica.security.jwt.jwk-set-uri with the JWKS endpoint of auth-service");
        }
        try {
            return JWKSourceBuilder.<SecurityContext>create(URI.create(properties.jwkSetUri()).toURL())
                    .retrying(true)
                    .outageTolerant(properties.jwkSetOutageTolerance().toMillis())
                    .build();
        } catch (MalformedURLException ex) {
            throw new IllegalStateException("clinica.security.jwt.jwk-set-uri is not a valid URL", ex);
        }
    }
}
