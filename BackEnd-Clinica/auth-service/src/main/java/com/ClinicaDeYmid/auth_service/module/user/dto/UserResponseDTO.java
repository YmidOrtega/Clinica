package com.ClinicaDeYmid.auth_service.module.user.dto;

import com.ClinicaDeYmid.auth_service.module.user.enums.StatusUser;

import java.util.Set;

public record UserResponseDTO(
        String uuid,
        String username,
        String email,
        boolean active,
        StatusUser status,
        Set<RoleDTO> roles
) {}
