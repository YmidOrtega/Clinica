package com.ClinicaDeYmid.patient_service.module.dto;

public record PatientsListDto(
        String identificationNumber,
        String name,
        String lastName
        ) {
}
