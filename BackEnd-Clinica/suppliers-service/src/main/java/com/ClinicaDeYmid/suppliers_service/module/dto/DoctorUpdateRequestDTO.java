package com.ClinicaDeYmid.suppliers_service.module.dto;

import java.util.List;

public record DoctorUpdateRequestDTO(
        String name,
        String lastName,
        String phoneNumber,
        String address,
        Double hourlyRate,
        Boolean active,
        List<Long> subSpecialtyIds,
        List<Long> allowedServiceTypeIds
) {}
