package com.ClinicaDeYmid.suppliers_service.module.dto;

import admissions_suppliers.dto.AttentionResponseDTO;
import admissions_suppliers.dto.ServiceTypeDTO;
import admissions_suppliers.dto.SubSpecialtyDTO;

import java.time.LocalDateTime;
import java.util.List;

public record DoctorResponse(
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
