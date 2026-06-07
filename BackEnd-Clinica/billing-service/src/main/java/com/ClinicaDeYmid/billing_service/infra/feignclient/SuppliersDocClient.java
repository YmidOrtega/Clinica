package com.ClinicaDeYmid.billing_service.infra.feignclient;

import com.ClinicaDeYmid.billing_service.infra.feignclient.dto.DoctorFeignDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "suppliers-service", contextId = "billingDoctorClient",
        path = "/api/v1/suppliers/doctors")
public interface SuppliersDocClient {

    @GetMapping("/{doctorId}")
    DoctorFeignDto getById(@PathVariable("doctorId") Long doctorId);
}
