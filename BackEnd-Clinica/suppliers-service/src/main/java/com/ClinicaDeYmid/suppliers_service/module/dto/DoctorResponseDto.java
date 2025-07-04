package com.ClinicaDeYmid.suppliers_service.module.dto;

import java.time.LocalDateTime;
import java.util.List;

public record DoctorResponseDto(
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
        LocalDateTime updatedAt,
        List<DoctorSpecialtyDto> specialties,
        List<AttentionGetDto> attentions
) {}
