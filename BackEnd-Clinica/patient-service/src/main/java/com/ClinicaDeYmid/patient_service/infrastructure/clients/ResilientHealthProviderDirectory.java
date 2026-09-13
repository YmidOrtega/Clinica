package com.ClinicaDeYmid.patient_service.infrastructure.clients;

import com.ClinicaDeYmid.patient_service.application.HealthProvider;
import com.ClinicaDeYmid.patient_service.application.HealthProviderDirectory;
import com.ClinicaDeYmid.patient_service.application.HealthProviderLookup;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import feign.FeignException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.client.circuitbreaker.CircuitBreaker;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
class ResilientHealthProviderDirectory implements HealthProviderDirectory {

    static final String CIRCUIT_BREAKER = "clients-service";

    private static final Logger log = LoggerFactory.getLogger(ResilientHealthProviderDirectory.class);

    private final HealthProviderClient client;
    private final CircuitBreaker circuitBreaker;
    private final Cache<String, HealthProviderLookup.Found> fresh;
    private final Cache<String, HealthProviderLookup.Found> lastKnown;

    ResilientHealthProviderDirectory(HealthProviderClient client, CircuitBreakerFactory<?, ?> circuitBreakers,
                                     HealthProviderCacheProperties properties) {
        this.client = client;
        this.circuitBreaker = circuitBreakers.create(CIRCUIT_BREAKER);
        this.fresh = cache(properties.freshTtl(), properties.maximumSize());
        this.lastKnown = cache(properties.lastKnownTtl(), properties.maximumSize());
    }

    @Override
    public HealthProviderLookup findByNit(String nit) {
        HealthProviderLookup.Found cached = fresh.getIfPresent(nit);
        if (cached != null) {
            return cached;
        }
        return circuitBreaker.run(() -> fetch(nit), failure -> fallback(nit, failure));
    }

    private HealthProviderLookup fetch(String nit) {
        try {
            HealthProviderClient.Payload payload = client.findByNit(nit);
            HealthProviderLookup.Found found = new HealthProviderLookup.Found(
                    new HealthProvider(nit, payload.socialReason(), payload.typeProvider()));
            fresh.put(nit, found);
            lastKnown.put(nit, found);
            return found;
        } catch (FeignException.NotFound notFound) {
            fresh.invalidate(nit);
            lastKnown.invalidate(nit);
            return new HealthProviderLookup.NotFound();
        }
    }

    private HealthProviderLookup fallback(String nit, Throwable failure) {
        HealthProviderLookup.Found known = lastKnown.getIfPresent(nit);
        log.warn("clients-service call failed ({}); answering with {}", failure.getClass().getSimpleName(),
                known != null ? "the last known health provider" : "an unavailable health provider");
        return known != null ? known : new HealthProviderLookup.Unavailable();
    }

    private static Cache<String, HealthProviderLookup.Found> cache(Duration ttl, long maximumSize) {
        return Caffeine.newBuilder().expireAfterWrite(ttl).maximumSize(maximumSize).build();
    }
}
