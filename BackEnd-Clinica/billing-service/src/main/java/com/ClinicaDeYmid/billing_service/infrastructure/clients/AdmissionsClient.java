package com.ClinicaDeYmid.billing_service.infrastructure.clients;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@FeignClient(name = "admissions-service")
interface AdmissionsClient {

    @GetMapping("/api/v1/admissions/episodes/{uuid}")
    EpisodePayload episode(@PathVariable("uuid") UUID uuid);

    @GetMapping("/api/v1/admissions/episodes/{uuid}/authorizations")
    List<AuthorizationPayload> authorizations(@PathVariable("uuid") UUID uuid);

    @JsonIgnoreProperties(ignoreUnknown = true)
    record EpisodePayload(UUID uuid, String number, UUID patientUuid, String cause, String kind, Status status,
                          Phase currentPhase, Attending attending, Coverage coverage) {

        @JsonIgnoreProperties(ignoreUnknown = true)
        record Status(String code) {
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        record Phase(UUID configurationServiceUuid, String configurationServiceName, Instant startedAt) {
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        record Attending(UUID practitionerUuid, String fullName, String registrationNumber) {
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        record Coverage(String status, UUID contractUuid, String contractNumber, UUID payerUuid, String detail) {
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record AuthorizationPayload(UUID uuid, String number, String type, String authorizedBy, BigDecimal copayment,
                                LocalDate validFrom, LocalDate validTo, Set<UUID> authorizedItems,
                                boolean coversEverything, String status) {

        boolean active() {
            return "ACTIVE".equals(status);
        }
    }
}
