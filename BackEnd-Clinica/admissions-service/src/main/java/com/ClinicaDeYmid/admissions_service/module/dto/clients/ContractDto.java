package com.ClinicaDeYmid.admissions_service.module.dto.clients;

public record ContractDto(
        Long id,
        String contractName,
        String contractNumber,
        String status
){}
