package com.ClinicaDeYmid.billing_service.infra.feignclient;

import com.ClinicaDeYmid.billing_service.infra.feignclient.dto.PatientFeignDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "patient-service", contextId = "billingPatientClient",
        path = "/api/v1/patients")
public interface PatientClient {

    @GetMapping("/{identificationNumber}")
    PatientFeignDto getByIdentificationNumber(
            @PathVariable("identificationNumber") String identificationNumber);
}
