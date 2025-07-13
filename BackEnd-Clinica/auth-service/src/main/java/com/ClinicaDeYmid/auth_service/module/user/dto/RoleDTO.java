package com.ClinicaDeYmid.auth_service.module.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record RoleDTO(
        @Schema(description = "ID único del rol", example = "1")
        Long id,

        @Schema(description = "Nombre del rol", example = "ADMIN")
        String name
) {}
