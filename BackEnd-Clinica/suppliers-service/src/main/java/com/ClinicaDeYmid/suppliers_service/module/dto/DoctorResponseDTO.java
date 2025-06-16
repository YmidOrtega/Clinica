package com.ClinicaDeYmid.suppliers_service.module.dto;

import java.time.LocalDateTime;

public record DoctorResponseDTO(
        Long id,
        Integer providerCode,
        String name,
        String lastName,
        String fullName,
        String identificationNumber,
        String phoneNumber,
        String email,
        String licenseNumber,
        String address,
        Double hourlyRate,
        Boolean active,
        LocalDateTime createdAt,
        LocalDateTime updatedAt

) {}
