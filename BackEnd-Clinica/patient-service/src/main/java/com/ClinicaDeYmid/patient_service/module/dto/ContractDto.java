package com.ClinicaDeYmid.patient_service.module.dto;

import java.time.LocalDate;

public record ContractDto(
        Long id,
        String contractName,
        String contractNumber,
        double agreedTariff,
        LocalDate startDate,
        LocalDate endDate,
        String status,
        Boolean active
){}
