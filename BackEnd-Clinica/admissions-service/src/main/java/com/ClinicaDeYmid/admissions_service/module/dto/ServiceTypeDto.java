package com.ClinicaDeYmid.admissions_service.module.dto;

import jakarta.validation.constraints.NotNull;

public record ServiceTypeDto(
        @NotNull Long id,
        @NotNull String name,
        @NotNull CareTypeDto careType,
        boolean active
) {}
