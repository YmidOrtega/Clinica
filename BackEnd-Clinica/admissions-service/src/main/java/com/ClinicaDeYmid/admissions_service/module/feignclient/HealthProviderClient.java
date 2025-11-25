package com.ClinicaDeYmid.admissions_service.module.feignclient;

import com.ClinicaDeYmid.admissions_service.module.dto.clients.GetHealthProviderDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "clients-service", path = "/api/v1/billing-service/health-providers")
public interface HealthProviderClient {

    @GetMapping("/{nit}/contracts/{contractId}")
    GetHealthProviderDto getHealthProviderByNitAndContract(
            @PathVariable("nit") String nit,
            @PathVariable("contractId") Long contractId);
}