package com.ClinicaDeYmid.auth_service.module.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import com.ClinicaDeYmid.auth_service.module.user.enums.StatusUser;

public record UserResponseDTO(
        @Schema(description = "UUID del usuario", example = "d9e8c123-4567-890a-bcde-f1234567890a")
        String uuid,

        @Schema(description = "Nombre de usuario", example = "yamid.ortega")
        String username,

        @Schema(description = "Correo electrónico", example = "usuario@clinica.com")
        String email,

        @Schema(description = "¿Está activo?", example = "true")
        boolean active,

        @Schema(description = "Estado del usuario", example = "ACTIVE", implementation = StatusUser.class)
        StatusUser status,

        @Schema(description = "Rol del usuario")
        RoleDTO role
) {}
