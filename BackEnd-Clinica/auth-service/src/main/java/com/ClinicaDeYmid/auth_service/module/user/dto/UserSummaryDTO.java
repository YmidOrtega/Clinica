package com.ClinicaDeYmid.auth_service.module.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record UserSummaryDTO(
        @Schema(description = "ID único del usuario", example = "12")
        Long id,

        @Schema(description = "Nombre de usuario", example = "yamid.ortega")
        String username
) {}
