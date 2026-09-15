package com.ClinicaDeYmid.commons.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@ConfigurationProperties("clinica.security")
public record ClinicaSecurityProperties(
        @DefaultValue Jwt jwt,
        @DefaultValue({"/actuator/health", "/actuator/health/**", "/actuator/info", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html"})
        List<String> publicPaths,
        @DefaultValue StepUp stepUp,
        @DefaultValue Revocation revocation,
        @DefaultValue Client client
) {

    public record Jwt(String jwkSetUri, String jwkSet, String issuer, @DefaultValue("clinica-api") List<String> audiences,
                      @DefaultValue("24h") Duration jwkSetOutageTolerance) {
    }

    public record StepUp(@DefaultValue("5m") Duration maxAge) {
    }

    public record Revocation(@DefaultValue("true") boolean enabled, @DefaultValue("auth.users.v1") String topic, String bootstrapServers) {
    }

    public record Client(String id, String tokenUri, String assertionKey, @DefaultValue Map<String, String> audiences,
                         @DefaultValue("60s") Duration renewBeforeExpiry) {

        public boolean configured() {
            return id != null && !id.isBlank() && tokenUri != null && !tokenUri.isBlank();
        }
    }
}
