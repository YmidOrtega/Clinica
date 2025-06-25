package com.ClinicaDeYmid.admissions_service.module.dto.attention;

import jakarta.validation.constraints.NotNull;

public record ConfigurationServiceResponseDto(
        @NotNull Long id,
        String serviceTypeName,
        String careTypeName,
        String locationName,
        boolean active
) {}