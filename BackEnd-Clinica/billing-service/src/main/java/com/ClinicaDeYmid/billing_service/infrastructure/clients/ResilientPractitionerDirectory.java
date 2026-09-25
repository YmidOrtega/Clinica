package com.ClinicaDeYmid.billing_service.infrastructure.clients;

import com.ClinicaDeYmid.billing_service.application.sale.PractitionerDirectory;
import com.ClinicaDeYmid.billing_service.application.sale.PractitionerLookup;
import feign.FeignException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.client.circuitbreaker.CircuitBreaker;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
class ResilientPractitionerDirectory implements PractitionerDirectory {

    static final String CIRCUIT_BREAKER = "practitioners-service";

    private static final Logger log = LoggerFactory.getLogger(ResilientPractitionerDirectory.class);

    private final PractitionersClient client;
    private final CircuitBreaker circuitBreaker;

    ResilientPractitionerDirectory(PractitionersClient client, CircuitBreakerFactory<?, ?> circuitBreakers) {
        this.client = client;
        this.circuitBreaker = circuitBreakers.create(CIRCUIT_BREAKER);
    }

    @Override
    public PractitionerLookup practitioner(UUID practitionerUuid) {
        return circuitBreaker.run(() -> {
            try {
                PractitionersClient.PractitionerPayload found = client.practitioner(practitionerUuid);
                return new PractitionerLookup.Found(found.fullName(),
                        found.registration() == null ? null : found.registration().number(),
                        found.status() != null && found.status().attends());
            } catch (FeignException.NotFound missing) {
                return new PractitionerLookup.NotFound();
            }
        }, failure -> {
            log.warn("practitioners-service did not answer for practitioner {} ({})", practitionerUuid,
                    failure.getClass().getSimpleName());
            return new PractitionerLookup.Unavailable();
        });
    }
}
