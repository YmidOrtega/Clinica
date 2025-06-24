package com.ClinicaDeYmid.admissions_service.module.dto.patient;

public record GetPatientDto(
        String identificationNumber,
        String name,
        String lastName,
        String dateOfBirth,
        String gender,
        String healthProviderName,
        String healthPolicyNumber
) {
}
