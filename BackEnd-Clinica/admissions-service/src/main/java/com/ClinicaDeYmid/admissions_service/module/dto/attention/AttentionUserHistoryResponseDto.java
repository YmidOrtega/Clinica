package com.ClinicaDeYmid.admissions_service.module.dto.attention;

import com.ClinicaDeYmid.admissions_service.module.dto.user.GetUserDto;
import com.ClinicaDeYmid.admissions_service.module.enums.UserActionType;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.time.LocalDateTime;

public record AttentionUserHistoryResponseDto(
        @JsonIgnore
        Long id,
        GetUserDto user,
        UserActionType actionType,
        LocalDateTime actionTimestamp,
        String observations
) {}