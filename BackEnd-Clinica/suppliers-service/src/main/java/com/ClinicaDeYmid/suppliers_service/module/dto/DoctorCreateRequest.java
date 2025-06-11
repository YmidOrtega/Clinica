package com.ClinicaDeYmid.suppliers_service.module.dto;

import jakarta.validation.constraints.*;

import java.util.List;

public record DoctorCreateRequest(
        @NotNull Integer providerCode,
        @NotBlank String name,
        @NotBlank String lastName,
        @NotBlank String identificationNumber,
        @NotBlank String phoneNumber,
        @Email @NotBlank String email,
        @NotBlank String licenseNumber,
        String address,
        Double hourlyRate,
        List<Long> subSpecialtyIds,
        List<Long> allowedServiceTypeIds
) {}

