package com.ClinicaDeYmid.admissions_service.infrastructure.clients;

import com.ClinicaDeYmid.admissions_service.application.patient.DeathReport;
import com.ClinicaDeYmid.admissions_service.application.patient.PatientDirectoryException;
import com.ClinicaDeYmid.admissions_service.application.patient.PatientLookup;
import com.ClinicaDeYmid.admissions_service.application.patient.PatientRegistry;
import com.ClinicaDeYmid.admissions_service.domain.patient.PatientReference;
import feign.FeignException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.client.circuitbreaker.CircuitBreaker;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.UUID;

@Component
class ResilientPatientRegistry implements PatientRegistry {

    static final String CIRCUIT_BREAKER = "patient-service";

    private static final String ALREADY_DECEASED = "INVALID_STATUS_TRANSITION";

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
            log.warn("patient-service lookup failed ({}); admission cannot resolve the patient right now",
                    failure.getClass().getSimpleName());
            return new PatientLookup.Unavailable();
        });
    }

    @Override
    public PatientReference.Unidentified registerUnidentified(PatientReference.Sex sex, int estimatedBirthYear,
                                                              String description) {
        return circuitBreaker.run(
                () -> (PatientReference.Unidentified) toReference(client.registerUnidentified(
                        new PatientRegistryClient.UnidentifiedRegistration(sex.name(), estimatedBirthYear, description))),
                failure -> {
                    log.error("patient-service refused or could not register an unidentified patient ({}); "
                            + "the admission was not created", failure.getClass().getSimpleName());
                    throw new PatientDirectoryException.CannotRegisterUnidentified();
                });
    }

    @Override
    public DeathReport recordDeath(UUID uuid, LocalDate dateOfDeath) {
        return circuitBreaker.run(() -> report(uuid, dateOfDeath), failure -> {
            log.warn("patient-service did not answer the death of {} ({})", uuid, failure.getClass().getSimpleName());
            return new DeathReport.Failed("patient-service no respondió al informar el fallecimiento");
        });
    }

    private DeathReport report(UUID uuid, LocalDate dateOfDeath) {
        try {
            return switch (lookup(uuid)) {
                case PatientLookup.Found found -> record(found.reference(), dateOfDeath);
                case PatientLookup.NotFound ignored ->
                        new DeathReport.Failed("el paciente ya no está en el directorio");
                case PatientLookup.Unavailable ignored ->
                        new DeathReport.Failed("patient-service no respondió al informar el fallecimiento");
            };
        } catch (FeignException.UnprocessableEntity alreadyDead) {
            if (alreadyDead.contentUTF8().contains(ALREADY_DECEASED)) {
                log.info("Patient {} was already recorded as deceased in patient-service", uuid);
                return new DeathReport.Recorded();
            }
            return new DeathReport.Failed("patient-service rechazó el fallecimiento");
        } catch (FeignException rejected) {
            log.warn("patient-service rejected the death of {} with status {}", uuid, rejected.status());
            return new DeathReport.Failed("patient-service rechazó el fallecimiento (HTTP " + rejected.status() + ")");
        }
    }

    private DeathReport record(PatientReference patient, LocalDate dateOfDeath) {
        if (patient.deceased()) {
            return new DeathReport.Recorded();
        }
        String version = "\"" + patient.version() + "\"";
        PatientRegistryClient.DeathRecord death = new PatientRegistryClient.DeathRecord(dateOfDeath);
        switch (patient) {
            case PatientReference.Registered registered ->
                    client.recordDeath(registered.uuid(), version, death);
            case PatientReference.Unidentified unidentified ->
                    client.recordUnidentifiedDeath(unidentified.uuid(), version, death);
        }
        log.info("Death of patient {} recorded in patient-service", patient.uuid());
        return new DeathReport.Recorded();
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
                payload.demographics().firstNames(), payload.demographics().lastNames(),
                payload.demographics().birthDate(),
                PatientReference.Sex.valueOf(payload.demographics().sex()),
                PatientReference.Registered.Status.valueOf(payload.status().code()), payload.status().dateOfDeath(),
                payload.affiliation().regime(), payload.affiliation().payerUuid());
    }

    private static PatientReference toReference(PatientRegistryClient.UnidentifiedPayload payload) {
        return new PatientReference.Unidentified(payload.uuid(), payload.version(), payload.code(),
                PatientReference.Sex.valueOf(payload.sex()), payload.estimatedBirthYear(),
                PatientReference.Unidentified.Status.valueOf(payload.status().code()),
                payload.status().identifiedPatientUuid(), payload.status().dateOfDeath());
    }
}
