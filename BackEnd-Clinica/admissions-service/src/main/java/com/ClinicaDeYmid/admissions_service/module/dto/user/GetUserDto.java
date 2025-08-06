package com.ClinicaDeYmid.admissions_service.module.dto.user;

public record GetUserDto(
        String uuid,
        String username,
        String email,
        boolean active,
        RoleDTO role
) {}
