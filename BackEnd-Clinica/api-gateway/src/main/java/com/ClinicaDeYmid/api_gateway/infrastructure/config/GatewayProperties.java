package com.ClinicaDeYmid.api_gateway.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.net.URI;
import java.time.Duration;
import java.util.List;

@ConfigurationProperties("clinica.gateway")
public record GatewayProperties(Frontend frontend, @DefaultValue Session session, Auth auth, @DefaultValue RateLimit rateLimit, Routes routes) {

    public record Frontend(List<String> origins, URI homeUrl) {

        public Frontend {
            origins = List.copyOf(origins);
        }
    }

    public record Session(@DefaultValue("12h") Duration absoluteLifetime) {
    }

    public record Auth(String publicUrl, String internalUrl, String clientId, String assertionKey, @DefaultValue("5m") Duration stepUpMaxAge,
                       @DefaultValue("30s") Duration refreshBeforeExpiry) {
    }

    public record RateLimit(@DefaultValue("1000") int perAddress, @DefaultValue("300") int perUser, @DefaultValue("1m") Duration window) {
    }

    public record Routes(String authService, String patientService, String clinicalHistoryService) {
    }
}
