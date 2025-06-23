package com.ClinicaDeYmid.admissions_service.module.dto;

import jakarta.validation.constraints.NotNull;

public record CareTypeDto(
        @NotNull Long id,
        @NotNull String name,
        boolean active
) {}
