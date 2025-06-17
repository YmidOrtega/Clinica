package com.ClinicaDeYmid.auth_service.module.user.dto;

import com.ClinicaDeYmid.auth_service.module.user.enums.StatusUser;
import jakarta.validation.constraints.NotNull;

public record UserStatusUpdateDTO(
        @NotNull(message = "El estado es obligatorio")
        StatusUser status,

        boolean active
) {}
