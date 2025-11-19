package com.ClinicaDeYmid.suppliers_service.module.dto.unavailability;

import com.ClinicaDeYmid.suppliers_service.module.enums.UnavailabilityType;

import java.time.LocalDate;

public record UnavailabilitySummaryDTO(
        Long id,
        UnavailabilityType type,
        String typeDisplayName,
        LocalDate startDate,
        LocalDate endDate,
        Boolean approved
) {}
