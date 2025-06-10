package com.ClinicaDeYmid.patient_service.module.controller;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "clients-service", path = "/api/billing-service/health-providers")
public interface HealthProviderClient {

    @GetMapping("/{nit}")
    HealthProviderDto getHealthProviderByNit(@PathVariable("nit") String nit);
}
