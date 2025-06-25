package com.ClinicaDeYmid.admissions_service.module.dto.attention;

import com.ClinicaDeYmid.admissions_service.module.enums.TypeOfAuthorization;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record AuthorizationRequestDto(
        Long id,
        @NotBlank @Size(max = 255) String authorizationNumber,
        @NotNull TypeOfAuthorization typeOfAuthorization,
        @NotNull List<Long> authorizedPortfolioIds
) {}