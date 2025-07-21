package com.ClinicaDeYmid.suppliers_service.module.feignclient;

import com.ClinicaDeYmid.suppliers_service.module.dto.AttentionGetDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "admissions-service", url = "${api.gateway.url}")
public interface AdmissionsClient {

    @GetMapping("/doctor/{doctorId}")
    AttentionGetDto getAttentionById(@PathVariable("doctorId") Long id);

}
