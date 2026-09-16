package com.ClinicaDeYmid.contracting_service.infrastructure.clients;

import com.ClinicaDeYmid.contracting_service.application.PatientDirectory;
import com.ClinicaDeYmid.contracting_service.application.PatientLookup;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import feign.FeignException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.circuitbreaker.CircuitBreaker;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
@EnableConfigurationProperties(PatientDirectoryProperties.class)
class ResilientPatientDirectory implements PatientDirectory {

    static final String CIRCUIT_BREAKER = "patient-service";

    private static final Logger log = LoggerFactory.getLogger(ResilientPatientDirectory.class);

    private final PatientDirectoryClient client;
    private final CircuitBreaker circuitBreaker;
    private final Cache<String, UUID> known;

    ResilientPatientDirectory(PatientDirectoryClient client, CircuitBreakerFactory<?, ?> circuitBreakers,
                              PatientDirectoryProperties properties) {
        this.client = client;
        this.circuitBreaker = circuitBreakers.create(CIRCUIT_BREAKER);
        this.known = Caffeine.newBuilder()
                .maximumSize(properties.maximumSize())
                .expireAfterWrite(properties.ttl())
                .build();
    }

    @Override
    public PatientLookup findByDocument(String documentType, String documentNumber) {
        String key = documentType + ":" + documentNumber;
        UUID cached = known.getIfPresent(key);
        if (cached != null) {
            return new PatientLookup.Found(cached);
        }
        return circuitBreaker.run(() -> lookup(key, documentType, documentNumber), failure -> {
            log.warn("patient-service lookup failed ({}); the member stays unverified", failure.getClass().getSimpleName());
            return new PatientLookup.Unavailable();
        });
    }

    private PatientLookup lookup(String key, String documentType, String documentNumber) {
        try {
            List<PatientDirectoryClient.Match> matches = client
                    .search(new PatientDirectoryClient.Search(new PatientDirectoryClient.Document(documentType, documentNumber)))
                    .content();
            if (matches == null || matches.isEmpty()) {
                return new PatientLookup.NotFound();
            }
            UUID uuid = matches.get(0).uuid();
            known.put(key, uuid);
            return new PatientLookup.Found(uuid);
        } catch (FeignException.NotFound | FeignException.BadRequest unknown) {
            return new PatientLookup.NotFound();
        }
    }
}
