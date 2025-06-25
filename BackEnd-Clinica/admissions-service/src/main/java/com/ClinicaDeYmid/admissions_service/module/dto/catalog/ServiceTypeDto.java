package com.ClinicaDeYmid.admissions_service.module.dto.catalog;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ServiceTypeDto(
        @NotNull Long id,
        @NotBlank @Size(max = 100) String name,
        @NotNull CareTypeDto careType,
        boolean active
) {}