package com.ClinicaDeYmid.patient_service.module.feignclient;

import com.ClinicaDeYmid.patient_service.module.dto.GetHealthProviderDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "clients-service", path = "/api/v1/billing-service")
public interface HealthProviderClient {

    @GetMapping("/health-providers/{nit}")
    GetHealthProviderDto getHealthProviderByNit(@PathVariable("nit") String nit);
}
