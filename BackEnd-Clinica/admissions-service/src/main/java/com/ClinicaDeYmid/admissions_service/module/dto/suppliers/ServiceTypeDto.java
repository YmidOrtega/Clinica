package com.ClinicaDeYmid.admissions_service.module.dto.suppliers;

import com.ClinicaDeYmid.admissions_service.module.dto.catalog.CareTypeDto;
import jakarta.validation.constraints.NotNull;

public record ServiceTypeDto(
        @NotNull Long id,
        @NotNull String name
) {}
