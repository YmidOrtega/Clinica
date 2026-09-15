package com.ClinicaDeYmid.auth_service.infrastructure.security;

import com.ClinicaDeYmid.commons.openbao.transit.TransitClient;
import com.ClinicaDeYmid.commons.openbao.transit.TransitKeys;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoderFactory;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.authorization.authentication.JwtClientAssertionDecoderFactory;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;

import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

final class TransitClientAssertionDecoderFactory implements JwtDecoderFactory<RegisteredClient> {

    private final TransitClient transit;
    private final Map<String, String> assertionKeys;
    private final Duration refreshInterval;
    private final Clock clock;
    private final Map<String, JwtDecoder> decoders = new ConcurrentHashMap<>();

    TransitClientAssertionDecoderFactory(TransitClient transit, Map<String, String> assertionKeys, Duration refreshInterval, Clock clock) {
        this.transit = transit;
        this.assertionKeys = Map.copyOf(assertionKeys);
        this.refreshInterval = refreshInterval;
        this.clock = clock;
    }

    @Override
    public JwtDecoder createDecoder(RegisteredClient client) {
        String key = assertionKeys.get(client.getClientId());
        if (key == null) {
            throw new OAuth2AuthenticationException(new OAuth2Error(OAuth2ErrorCodes.INVALID_CLIENT,
                    "The client has no assertion key registered", null));
        }
        return decoders.computeIfAbsent(client.getClientId(), id -> decoder(client, key));
    }

    private JwtDecoder decoder(RegisteredClient client, String key) {
        DefaultJWTProcessor<SecurityContext> processor = new DefaultJWTProcessor<>();
        processor.setJWSKeySelector(new JWSVerificationKeySelector<>(JWSAlgorithm.ES256,
                TransitJwkSource.allVersions(new TransitKeys(transit, key, refreshInterval, clock))));
        NimbusJwtDecoder decoder = new NimbusJwtDecoder(processor);
        decoder.setJwtValidator(JwtClientAssertionDecoderFactory.DEFAULT_JWT_VALIDATOR_FACTORY.apply(client));
        return decoder;
    }
}
