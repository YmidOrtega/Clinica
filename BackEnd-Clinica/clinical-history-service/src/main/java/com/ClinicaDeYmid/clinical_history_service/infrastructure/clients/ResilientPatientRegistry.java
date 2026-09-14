package com.ClinicaDeYmid.clinical_history_service.infrastructure.clients;

import com.ClinicaDeYmid.clinical_history_service.application.patient.PatientLookup;
import com.ClinicaDeYmid.clinical_history_service.application.patient.PatientRegistry;
import com.ClinicaDeYmid.clinical_history_service.domain.patient.PatientReference;
import feign.FeignException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.client.circuitbreaker.CircuitBreaker;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
class ResilientPatientRegistry implements PatientRegistry {

    static final String CIRCUIT_BREAKER = "patient-service";

    private static final Logger log = LoggerFactory.getLogger(ResilientPatientRegistry.class);

    private final PatientRegistryClient client;
    private final CircuitBreaker circuitBreaker;

    ResilientPatientRegistry(PatientRegistryClient client, CircuitBreakerFactory<?, ?> circuitBreakers) {
        this.client = client;
        this.circuitBreaker = circuitBreakers.create(CIRCUIT_BREAKER);
    }

    @Override
    public PatientLookup fetch(UUID uuid) {
        return circuitBreaker.run(() -> lookup(uuid), failure -> {
            log.warn("patient-service lookup failed ({}); the patient cannot be resolved right now", failure.getClass().getSimpleName());
            return new PatientLookup.Unavailable();
        });
    }

    private PatientLookup lookup(UUID uuid) {
        try {
            return new PatientLookup.Found(toReference(client.findPatient(uuid)));
        } catch (FeignException.NotFound notRegistered) {
            try {
                return new PatientLookup.Found(toReference(client.findUnidentified(uuid)));
            } catch (FeignException.NotFound notUnidentified) {
                return new PatientLookup.NotFound();
            }
        }
    }

    private static PatientReference toReference(PatientRegistryClient.RegisteredPayload payload) {
        return new PatientReference.Registered(payload.uuid(), payload.version(),
                new PatientReference.Document(payload.document().type(), payload.document().number()),
                payload.demographics().firstNames(), payload.demographics().lastNames(), payload.demographics().birthDate(),
                PatientReference.Sex.valueOf(payload.demographics().sex()),
                PatientReference.Registered.Status.valueOf(payload.status().code()), payload.status().dateOfDeath(),
                payload.affiliation().regime(), payload.affiliation().healthProviderNit());
    }

    private static PatientReference toReference(PatientRegistryClient.UnidentifiedPayload payload) {
        return new PatientReference.Unidentified(payload.uuid(), payload.version(), payload.code(),
                PatientReference.Sex.valueOf(payload.sex()), payload.estimatedBirthYear(),
                PatientReference.Unidentified.Status.valueOf(payload.status().code()),
                payload.status().identifiedPatientUuid(), payload.status().dateOfDeath());
    }
}
