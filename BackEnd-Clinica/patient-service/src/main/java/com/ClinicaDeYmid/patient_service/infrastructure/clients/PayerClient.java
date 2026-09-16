package com.ClinicaDeYmid.patient_service.infrastructure.clients;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.UUID;

@FeignClient(name = "contracting-service", path = "/api/v1/payers", configuration = PayerClientConfiguration.class)
interface PayerClient {

    @GetMapping("/{uuid}")
    Payload findByUuid(@PathVariable("uuid") UUID uuid);

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Payload(UUID uuid, String socialReason, String nit, String type, StatusPayload status) {

        @JsonIgnoreProperties(ignoreUnknown = true)
        record StatusPayload(String code, boolean contractable) {
        }
    }
}
