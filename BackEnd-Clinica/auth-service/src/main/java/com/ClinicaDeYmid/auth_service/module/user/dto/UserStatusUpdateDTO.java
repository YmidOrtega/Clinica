package com.ClinicaDeYmid.auth_service.module.user.dto;

import com.ClinicaDeYmid.auth_service.module.user.enums.StatusUser;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Request DTO for updating user status")
public record UserStatusUpdateDTO(
        @NotNull(message = "El estado es obligatorio")
        @Schema(description = "Nuevo estado del usuario", requiredMode = Schema.RequiredMode.REQUIRED)
        StatusUser status,

        @NotNull(message = "El campo active es obligatorio")
        @Schema(description = "Indica si el usuario está activo", requiredMode = Schema.RequiredMode.REQUIRED)
        Boolean active
) {}