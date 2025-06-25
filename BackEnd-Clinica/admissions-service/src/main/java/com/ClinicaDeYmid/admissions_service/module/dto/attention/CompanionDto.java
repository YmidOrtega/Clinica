package com.ClinicaDeYmid.admissions_service.module.dto.attention;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CompanionDto(
        @NotBlank @Size(max = 255) String fullName,
        @NotBlank @Size(max = 20) String phoneNumber,
        @NotBlank @Size(max = 100) String relationship
) {}