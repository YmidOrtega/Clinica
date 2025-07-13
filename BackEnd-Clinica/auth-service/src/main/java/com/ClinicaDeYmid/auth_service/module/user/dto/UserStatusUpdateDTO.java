package com.ClinicaDeYmid.auth_service.module.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import com.ClinicaDeYmid.auth_service.module.user.enums.StatusUser;
import jakarta.validation.constraints.NotNull;

public record UserStatusUpdateDTO(
        @Schema(description = "Nuevo estado del usuario", example = "ACTIVE", required = true, implementation = StatusUser.class)
        @NotNull(message = "El estado es obligatorio")
        StatusUser status,

        @Schema(description = "¿Está activo?", example = "true")
        boolean active
) {}
