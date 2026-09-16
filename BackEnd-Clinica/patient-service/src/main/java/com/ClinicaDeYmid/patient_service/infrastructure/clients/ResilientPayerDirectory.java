package com.ClinicaDeYmid.patient_service.infrastructure.clients;

import com.ClinicaDeYmid.patient_service.application.Payer;
import com.ClinicaDeYmid.patient_service.application.PayerDirectory;
import com.ClinicaDeYmid.patient_service.application.PayerLookup;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import feign.FeignException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.circuitbreaker.CircuitBreaker;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;

@Component
@EnableConfigurationProperties(PayerCacheProperties.class)
class ResilientPayerDirectory implements PayerDirectory {

    static final String CIRCUIT_BREAKER = "contracting-service";

    private static final Logger log = LoggerFactory.getLogger(ResilientPayerDirectory.class);

    private final PayerClient client;
    private final CircuitBreaker circuitBreaker;
    private final Cache<UUID, PayerLookup.Found> fresh;
    private final Cache<UUID, PayerLookup.Found> lastKnown;

    ResilientPayerDirectory(PayerClient client, CircuitBreakerFactory<?, ?> circuitBreakers,
                            PayerCacheProperties properties) {
        this.client = client;
        this.circuitBreaker = circuitBreakers.create(CIRCUIT_BREAKER);
        this.fresh = cache(properties.freshTtl(), properties.maximumSize());
        this.lastKnown = cache(properties.lastKnownTtl(), properties.maximumSize());
    }

    @Override
    public PayerLookup findByUuid(UUID uuid) {
        PayerLookup.Found cached = fresh.getIfPresent(uuid);
        if (cached != null) {
            return cached;
        }
        return circuitBreaker.run(() -> fetch(uuid), failure -> fallback(uuid, failure));
    }

    private PayerLookup fetch(UUID uuid) {
        try {
            PayerClient.Payload payload = client.findByUuid(uuid);
            PayerLookup.Found found = new PayerLookup.Found(
                    new Payer(uuid, payload.nit(), payload.socialReason(), payload.type()));
            fresh.put(uuid, found);
            lastKnown.put(uuid, found);
            return found;
        } catch (FeignException.NotFound notFound) {
            fresh.invalidate(uuid);
            lastKnown.invalidate(uuid);
            return new PayerLookup.NotFound();
        }
    }

    private PayerLookup fallback(UUID uuid, Throwable failure) {
        log.warn("contracting-service call failed ({}); answering with {}", failure.getClass().getSimpleName(),
                lastKnown.getIfPresent(uuid) == null ? "no payer" : "the last known payer");
        PayerLookup.Found known = lastKnown.getIfPresent(uuid);
        return known == null ? new PayerLookup.Unavailable() : known;
    }

    private static Cache<UUID, PayerLookup.Found> cache(Duration ttl, long maximumSize) {
        return Caffeine.newBuilder().expireAfterWrite(ttl).maximumSize(maximumSize).build();
    }
}
