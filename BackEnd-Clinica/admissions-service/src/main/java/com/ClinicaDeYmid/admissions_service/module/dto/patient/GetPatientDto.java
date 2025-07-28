package com.ClinicaDeYmid.admissions_service.module.dto.patient;

public record GetPatientDto(
        String identificationNumber,
        String name,
        String lastName,
        String dateOfBirth,
        String gender,
        String occupationName,
        String healthPolicyNumber,
        String localityName,
        String address,
        String mobile
) {
    public String fullName() {
        return name + " " + lastName;
    }
}
