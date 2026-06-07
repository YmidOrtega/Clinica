package com.ClinicaDeYmid.billing_service.infra.feignclient;

import com.ClinicaDeYmid.billing_service.infra.feignclient.dto.PortfolioFeignDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "clients-service", contextId = "clientsPortfolioClient",
        path = "/api/v1/portfolios")
public interface ClientsPortfolioClient {

    @GetMapping("/{id}")
    PortfolioFeignDto getById(@PathVariable("id") Long id);
}
