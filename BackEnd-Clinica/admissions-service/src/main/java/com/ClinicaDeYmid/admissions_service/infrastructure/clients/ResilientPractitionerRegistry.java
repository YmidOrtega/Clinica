package com.ClinicaDeYmid.admissions_service.infrastructure.clients;

import com.ClinicaDeYmid.admissions_service.application.practitioner.PractitionerLookup;
import com.ClinicaDeYmid.admissions_service.application.practitioner.PractitionerRegistry;
import com.ClinicaDeYmid.admissions_service.domain.practitioner.PractitionerReference;
import feign.FeignException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.client.circuitbreaker.CircuitBreaker;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
class ResilientPractitionerRegistry implements PractitionerRegistry {

    static final String CIRCUIT_BREAKER = "practitioners-service";

    private static final Logger log = LoggerFactory.getLogger(ResilientPractitionerRegistry.class);

    private final PractitionerDirectoryClient client;
    private final CircuitBreaker circuitBreaker;

    ResilientPractitionerRegistry(PractitionerDirectoryClient client, CircuitBreakerFactory<?, ?> circuitBreakers) {
        this.client = client;
        this.circuitBreaker = circuitBreakers.create(CIRCUIT_BREAKER);
    }

    @Override
    public PractitionerLookup fetch(UUID practitionerUuid) {
        return circuitBreaker.run(() -> lookup(practitionerUuid), failure -> {
            log.warn("practitioners-service did not answer ({})", failure.getClass().getSimpleName());
            return new PractitionerLookup.Unavailable();
        });
    }

    private PractitionerLookup lookup(UUID practitionerUuid) {
        try {
            return new PractitionerLookup.Found(toReference(client.find(practitionerUuid)));
        } catch (FeignException.NotFound unknown) {
            return new PractitionerLookup.NotFound();
        }
    }

    private static PractitionerReference toReference(PractitionerDirectoryClient.PractitionerPayload payload) {
        return new PractitionerReference(payload.uuid(), payload.version(), payload.fullName(),
                payload.registration().number(), principalSpecialty(payload), payload.status().code(),
                payload.account() == null ? null : payload.account().userUuid());
    }

    private static String principalSpecialty(PractitionerDirectoryClient.PractitionerPayload payload) {
        if (payload.specialties() == null) {
            return null;
        }
        return payload.specialties().stream()
                .filter(PractitionerDirectoryClient.PractitionerPayload.Specialty::principal)
                .findFirst()
                .map(specialty -> specialty.subSpecialtyName() == null
                        ? specialty.specialtyName()
                        : specialty.specialtyName() + " · " + specialty.subSpecialtyName())
                .orElse(null);
    }
}
