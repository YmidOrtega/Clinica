package com.ClinicaDeYmid.admissions_service.module.dto.attention;

import com.ClinicaDeYmid.admissions_service.module.enums.TypeOfAuthorization;
import java.time.LocalDateTime;
import java.util.List;

public record AuthorizationResponseDto(
        Long id,
        String authorizationNumber,
        TypeOfAuthorization typeOfAuthorization,
        String authorizationBy,
        Double copaymentValue,
        List<Long> authorizedPortfolioIds,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}