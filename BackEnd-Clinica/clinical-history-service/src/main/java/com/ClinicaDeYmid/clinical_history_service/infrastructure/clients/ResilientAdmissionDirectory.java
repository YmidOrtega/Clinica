package com.ClinicaDeYmid.clinical_history_service.infrastructure.clients;

import com.ClinicaDeYmid.clinical_history_service.application.admission.AdmissionDirectory;
import com.ClinicaDeYmid.clinical_history_service.application.admission.AdmissionLookup;
import feign.FeignException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.client.circuitbreaker.CircuitBreaker;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
class ResilientAdmissionDirectory implements AdmissionDirectory {

    static final String CIRCUIT_BREAKER = "admissions-service";

    private static final Logger log = LoggerFactory.getLogger(ResilientAdmissionDirectory.class);

    private final AdmissionDirectoryClient client;
    private final CircuitBreaker circuitBreaker;

    ResilientAdmissionDirectory(AdmissionDirectoryClient client, CircuitBreakerFactory<?, ?> circuitBreakers) {
        this.client = client;
        this.circuitBreaker = circuitBreakers.create(CIRCUIT_BREAKER);
    }

    @Override
    public AdmissionLookup find(UUID admissionUuid) {
        return circuitBreaker.run(() -> lookup(admissionUuid), failure -> {
            log.warn("admissions-service did not answer for episode {} ({}); the encounter opens unverified",
                    admissionUuid, failure.getClass().getSimpleName());
            return new AdmissionLookup.Unavailable();
        });
    }

    private AdmissionLookup lookup(UUID admissionUuid) {
        try {
            client.findEpisode(admissionUuid);
            return new AdmissionLookup.Found();
        } catch (FeignException.NotFound unknown) {
            return new AdmissionLookup.NotFound();
        }
    }
}
