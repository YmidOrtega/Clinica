package com.ClinicaDeYmid.auth_service.module.user.dto;

import java.util.Set;

public record UserResponseDTO(
        Long id,
        String username,
        String email,
        Set<RoleDTO> roles
) {}
