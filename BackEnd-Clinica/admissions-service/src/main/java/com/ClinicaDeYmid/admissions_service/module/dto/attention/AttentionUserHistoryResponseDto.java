package com.ClinicaDeYmid.admissions_service.module.dto.attention;

import io.swagger.v3.oas.annotations.media.Schema;
import com.ClinicaDeYmid.admissions_service.module.dto.user.GetUserDto;
import com.ClinicaDeYmid.admissions_service.module.enums.UserActionType;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.time.LocalDateTime;

public record AttentionUserHistoryResponseDto(
        @Schema(hidden = true)
        @JsonIgnore
        Long id,

        @Schema(description = "Usuario que realizó la acción")
        GetUserDto user,

        @Schema(description = "Tipo de acción", example = "CREATED", implementation = UserActionType.class)
        UserActionType actionType,

        @Schema(description = "Fecha y hora de la acción", example = "2024-07-12T15:40:00")
        LocalDateTime actionTimestamp,

        @Schema(description = "Observaciones sobre la acción", example = "Usuario creó la atención")
        String observations
) {}
