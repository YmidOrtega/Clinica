package com.ClinicaDeYmid.admissions_service.module.feignclient;

import com.ClinicaDeYmid.admissions_service.module.dto.suppliers.GetDoctorDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "suppliers-service", path = "/api/v1/suppliers/doctors")
public interface DoctorClient {

    @GetMapping("/{id}")
    GetDoctorDto getDoctorById(@PathVariable("id") Long id);


}
