package com.ClinicaDeYmid.admissions_service.module.feignclient;

import com.ClinicaDeYmid.admissions_service.module.dto.patient.GetPatientDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "patient-service", path = "/api/v1/patients")
public interface PatientClient {

    @GetMapping("/{identificationNumber}")
    GetPatientDto getPatientByIdentificationNumber(@PathVariable("identificationNumber") String identificationNumber);
}