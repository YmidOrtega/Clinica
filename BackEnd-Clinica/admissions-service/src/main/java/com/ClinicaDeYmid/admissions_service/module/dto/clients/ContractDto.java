package com.ClinicaDeYmid.admissions_service.module.dto.clients;

import java.time.LocalDate;

public record ContractDto(
        Long id,
        String contractName,
        String contractNumber,
        String status
){}
