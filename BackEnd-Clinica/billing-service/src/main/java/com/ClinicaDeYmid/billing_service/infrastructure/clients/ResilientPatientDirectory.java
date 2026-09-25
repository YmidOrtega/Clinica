package com.ClinicaDeYmid.billing_service.infrastructure.clients;

import com.ClinicaDeYmid.billing_service.application.context.PatientDetails;
import com.ClinicaDeYmid.billing_service.application.context.PatientDirectory;
import com.ClinicaDeYmid.billing_service.application.context.PatientLookup;
import feign.FeignException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.client.circuitbreaker.CircuitBreaker;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
class ResilientPatientDirectory implements PatientDirectory {

    static final String CIRCUIT_BREAKER = "patient-service";

    private static final Logger log = LoggerFactory.getLogger(ResilientPatientDirectory.class);

    private final PatientRegistryClient client;
    private final CircuitBreaker circuitBreaker;

    ResilientPatientDirectory(PatientRegistryClient client, CircuitBreakerFactory<?, ?> circuitBreakers) {
        this.client = client;
        this.circuitBreaker = circuitBreakers.create(CIRCUIT_BREAKER);
    }

    @Override
    public PatientLookup patient(UUID patientUuid) {
        return circuitBreaker.run(() -> lookup(patientUuid), failure -> {
            log.warn("patient-service did not answer for patient {} ({})", patientUuid,
                    failure.getClass().getSimpleName());
            return new PatientLookup.Unavailable();
        });
    }

    private PatientLookup lookup(UUID patientUuid) {
        try {
            PatientRegistryClient.RegisteredPayload patient = client.findPatient(patientUuid);
            return new PatientLookup.Found(new PatientDetails.Registered(patient.uuid(), patient.document().type(),
                    patient.document().number(), patient.demographics().firstNames(),
                    patient.demographics().lastNames(), patient.demographics().birthDate(),
                    patient.demographics().sex(), patient.affiliation() == null ? null : patient.affiliation().regime()));
        } catch (FeignException.NotFound notRegistered) {
            try {
                PatientRegistryClient.UnidentifiedPayload patient = client.findUnidentified(patientUuid);
                return new PatientLookup.Found(new PatientDetails.Unidentified(patient.uuid(), patient.code(),
                        patient.sex(), patient.estimatedBirthYear()));
            } catch (FeignException.NotFound notUnidentified) {
                return new PatientLookup.NotFound();
            }
        }
    }
}
