package com.ClinicaDeYmid.auth_service.module.user.dto;

import com.ClinicaDeYmid.auth_service.module.user.enums.StatusUser;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;

public record UserListDTO(
        Long id,
        String username,
        String email,
        String phoneNumber,

        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        LocalDateTime createdAt,

        boolean active,
        StatusUser status,
        RoleDTO role
) {}
