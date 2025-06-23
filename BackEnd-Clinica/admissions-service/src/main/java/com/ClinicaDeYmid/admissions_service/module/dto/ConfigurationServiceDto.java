package com.ClinicaDeYmid.admissions_service.module.dto;

import jakarta.validation.constraints.NotNull;

public record ConfigurationServiceDto(
        @NotNull Long id,
        @NotNull ServiceTypeDto serviceType,
        @NotNull LocationDto location,
        boolean active
) {}
