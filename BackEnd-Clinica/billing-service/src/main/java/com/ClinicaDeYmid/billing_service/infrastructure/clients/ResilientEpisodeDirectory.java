package com.ClinicaDeYmid.billing_service.infrastructure.clients;

import com.ClinicaDeYmid.billing_service.application.context.EpisodeDetails;
import com.ClinicaDeYmid.billing_service.application.context.EpisodeDirectory;
import com.ClinicaDeYmid.billing_service.application.context.EpisodeLookup;
import feign.FeignException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.client.circuitbreaker.CircuitBreaker;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.UUID;

@Component
class ResilientEpisodeDirectory implements EpisodeDirectory {

    static final String CIRCUIT_BREAKER = "admissions-service";

    private static final Logger log = LoggerFactory.getLogger(ResilientEpisodeDirectory.class);

    private final AdmissionsClient client;
    private final CircuitBreaker circuitBreaker;

    ResilientEpisodeDirectory(AdmissionsClient client, CircuitBreakerFactory<?, ?> circuitBreakers) {
        this.client = client;
        this.circuitBreaker = circuitBreakers.create(CIRCUIT_BREAKER);
    }

    @Override
    public EpisodeLookup episode(UUID admissionUuid) {
        return circuitBreaker.run(() -> lookup(admissionUuid), failure -> {
            log.warn("admissions-service did not answer for episode {} ({})", admissionUuid,
                    failure.getClass().getSimpleName());
            return new EpisodeLookup.Unavailable();
        });
    }

    private EpisodeLookup lookup(UUID admissionUuid) {
        AdmissionsClient.EpisodePayload episode;
        try {
            episode = client.episode(admissionUuid);
        } catch (FeignException.NotFound missing) {
            return new EpisodeLookup.NotFound();
        }
        var authorizations = client.authorizations(admissionUuid).stream()
                .filter(AdmissionsClient.AuthorizationPayload::active)
                .map(authorization -> new EpisodeDetails.Authorization(authorization.uuid(), authorization.number(),
                        authorization.type(), authorization.authorizedBy(), authorization.copayment(),
                        authorization.validFrom(), authorization.validTo(),
                        authorization.authorizedItems() == null ? Set.of() : Set.copyOf(authorization.authorizedItems()),
                        authorization.coversEverything()))
                .toList();
        return new EpisodeLookup.Found(new EpisodeDetails(episode.uuid(), episode.number(), episode.cause(),
                episode.kind(), episode.status() == null ? null : episode.status().code(),
                episode.currentPhase() == null ? null : new EpisodeDetails.Phase(
                        episode.currentPhase().configurationServiceUuid(),
                        episode.currentPhase().configurationServiceName(), episode.currentPhase().startedAt()),
                episode.attending() == null ? null : new EpisodeDetails.Attending(
                        episode.attending().practitionerUuid(), episode.attending().fullName(),
                        episode.attending().registrationNumber()),
                episode.coverage() == null ? null : new EpisodeDetails.Coverage(episode.coverage().status(),
                        episode.coverage().payerUuid(), episode.coverage().contractUuid(),
                        episode.coverage().contractNumber(), episode.coverage().detail()),
                authorizations));
    }
}
