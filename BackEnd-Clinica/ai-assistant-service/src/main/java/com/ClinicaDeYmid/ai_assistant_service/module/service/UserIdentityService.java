package com.ClinicaDeYmid.ai_assistant_service.module.service;

import com.ClinicaDeYmid.ai_assistant_service.module.dto.auth.UserResponseDto;
import com.ClinicaDeYmid.ai_assistant_service.module.feignclient.AuthUserClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class UserIdentityService {

    private final AuthUserClient authUserClient;

    public Long resolveUserId(Long userId, String uuid, String email) {
        if (userId != null) {
            return userId;
        }

        if (uuid == null || uuid.isBlank()) {
            throw new RuntimeException("Authenticated user UUID is missing");
        }

        try {
            UserResponseDto user = authUserClient.getUserByUuid(uuid);
            if (user == null || user.id() == null) {
                throw new RuntimeException("Authenticated user could not be resolved from auth-service");
            }
            return user.id();
        } catch (Exception e) {
            log.error("Failed to resolve userId for user {} with UUID {}: {}", email, uuid, e.getMessage(), e);
            throw new RuntimeException("Authenticated user could not be resolved from auth-service", e);
        }
    }
}
