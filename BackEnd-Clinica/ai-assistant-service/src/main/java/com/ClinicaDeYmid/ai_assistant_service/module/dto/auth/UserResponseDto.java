package com.ClinicaDeYmid.ai_assistant_service.module.dto.auth;

public record UserResponseDto(
        Long id,
        String uuid,
        String username,
        String email,
        boolean active
) {}
