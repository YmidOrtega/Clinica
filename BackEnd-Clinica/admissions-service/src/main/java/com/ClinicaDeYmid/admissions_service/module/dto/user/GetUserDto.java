package com.ClinicaDeYmid.admissions_service.module.dto.user;

import java.util.Set;

public record GetUserDto(
        String uuid,
        String username,
        String email,
        boolean active,
        RoleDTO role
) {}
