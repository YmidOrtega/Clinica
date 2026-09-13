package com.ClinicaDeYmid.patient_service.infrastructure.clients;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "clients-service", path = "/api/v1/billing-service/health-providers")
interface HealthProviderClient {

    @GetMapping("/{nit}")
    Payload findByNit(@PathVariable("nit") String nit);

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Payload(String socialReason, String typeProvider) {
    }
}
