package com.ClinicaDeYmid.ai_assistant_service.module.feignclient;

import com.ClinicaDeYmid.ai_assistant_service.module.dto.auth.UserResponseDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "auth-service", path = "/api/v1/auth/users")
public interface AuthUserClient {

    @GetMapping("/uuid/{uuid}")
    UserResponseDto getUserByUuid(@PathVariable("uuid") String uuid);
}
