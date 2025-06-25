package com.ClinicaDeYmid.admissions_service.module.dto.attention;

import com.ClinicaDeYmid.admissions_service.module.enums.UserActionType;
import java.time.LocalDateTime;

public record AttentionUserHistoryResponseDto(
        Long id,
        Long userId,
        UserActionType actionType,
        LocalDateTime actionTimestamp,
        String observations
) {}