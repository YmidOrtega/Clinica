package com.ClinicaDeYmid.admissions_service.module.feignclient;

import com.ClinicaDeYmid.admissions_service.module.dto.user.GetUserDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "auth-service", path = "/api/v1/auth/users")
public interface UserClient {

    @GetMapping("/{id}")
    GetUserDto getUserById(@PathVariable("id") Long id);
}