package com.ClinicaDeYmid.patient_service.module.patient.dto;

import java.time.LocalDate;

public record UpdatePatientDto(
        String identificationType,
        String name,
        String lastName,
        LocalDate dateOfBirth,
        String placeOfBirthName,
        String placeOfIssuanceName,
        String disability,
        String language,
        String gender,
        String occupationName,
        String maritalStatus,
        String religion,
        String typeOfAffiliation,
        String affiliationNumber,
        String healthPolicyId,
        String healthPolicyNumber,
        String mothersName,
        String fathersName,
        String zone,
        String localityName,
        String address,
        String phone,
        String mobile,
        String email
) {
}
