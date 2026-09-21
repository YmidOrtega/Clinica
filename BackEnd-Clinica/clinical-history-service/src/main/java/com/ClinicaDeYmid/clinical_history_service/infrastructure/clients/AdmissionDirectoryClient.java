package com.ClinicaDeYmid.clinical_history_service.infrastructure.clients;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.UUID;

@FeignClient(name = "admissions-service")
interface AdmissionDirectoryClient {

    @GetMapping("/api/v1/admissions/episodes/{uuid}")
    EpisodePayload findEpisode(@PathVariable("uuid") UUID uuid);

    @JsonIgnoreProperties(ignoreUnknown = true)
    record EpisodePayload(UUID uuid, String number) {
    }
}
