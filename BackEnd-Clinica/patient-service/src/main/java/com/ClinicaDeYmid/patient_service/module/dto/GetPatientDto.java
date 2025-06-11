package com.ClinicaDeYmid.patient_service.module.dto;

import java.time.LocalDate;

public record GetPatientDto(
        String uuid,
        String identificationType,
        String identificationNumber,
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
        GetClientDto clientInfo,
        String healthPolicyNumber,
        String mothersName,
        String fathersName,
        String zone,
        String localityName,
        String address,
        String phone,
        String mobile,
        String email
) {}
