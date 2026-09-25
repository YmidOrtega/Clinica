package com.ClinicaDeYmid.billing_service.infrastructure.clients;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.UUID;

@FeignClient(name = "practitioners-service")
interface PractitionersClient {

    @GetMapping("/api/v1/practitioners/{uuid}")
    PractitionerPayload practitioner(@PathVariable("uuid") UUID uuid);

    @JsonIgnoreProperties(ignoreUnknown = true)
    record PractitionerPayload(UUID uuid, String fullName, Registration registration, Status status) {

        @JsonIgnoreProperties(ignoreUnknown = true)
        record Registration(String number) {
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        record Status(String code, boolean attends) {
        }
    }
}
