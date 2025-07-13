package com.ClinicaDeYmid.auth_service.module.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import com.ClinicaDeYmid.auth_service.module.user.enums.StatusUser;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDateTime;

public record UserListDTO(
        @Schema(description = "ID único del usuario", example = "1")
        Long id,

        @Schema(description = "Nombre de usuario", example = "yamid.ortega")
        String username,

        @Schema(description = "Correo electrónico del usuario", example = "usuario@clinica.com")
        String email,

        @Schema(description = "Teléfono del usuario", example = "+573001234567")
        String phoneNumber,

        @Schema(description = "Fecha de creación del usuario", example = "2024-07-13 09:30:00")
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        LocalDateTime createdAt,

        @Schema(description = "Si el usuario está activo", example = "true")
        boolean active,

        @Schema(description = "Estado del usuario", example = "ACTIVE", implementation = StatusUser.class)
        StatusUser status,

        @Schema(description = "Rol del usuario")
        RoleDTO role
) {}
