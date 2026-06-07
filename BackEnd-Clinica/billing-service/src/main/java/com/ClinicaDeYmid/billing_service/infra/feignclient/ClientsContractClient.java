package com.ClinicaDeYmid.billing_service.infra.feignclient;

import com.ClinicaDeYmid.billing_service.infra.feignclient.dto.ContractFeignDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "clients-service", contextId = "clientsContractClient",
        path = "/api/v1/contracts")
public interface ClientsContractClient {

    @GetMapping("/{id}")
    ContractFeignDto getById(@PathVariable("id") Long id);
}
