package com.ClinicaDeYmid.patient_service.module.patient.dto;

import com.ClinicaDeYmid.patient_service.module.patient.model.Occupation;
import com.ClinicaDeYmid.patient_service.module.patient.model.Site;
import com.ClinicaDeYmid.patient_service.module.patient.model.enums.*;
import com.ClinicaDeYmid.patient_service.module.patient.validation.ParentsRequiredForMinor;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;

import java.time.LocalDate;
import java.util.UUID;

@ParentsRequiredForMinor

public record PatientDTO(
        UUID uuid,
        @NotNull IdentificationType identificationType,
        @NotBlank String identificationNumber,
        @NotBlank String name,
        @NotBlank String lastName,
        @NotNull @Past LocalDate dateOfBirth,
        @NotNull Site placeOfBirth,
        @NotNull Site placeOfIssuance,
        @NotNull Disability disability,
        @NotNull Language language,
        @NotNull Gender gender,
        @NotNull Occupation occupation,
        @NotNull MaritalStatus maritalStatus,
        @NotNull Religion religion,
        @NotNull TypeOfAffiliation typeOfAffiliation,
        String affiliationNumber,
        @NotNull UUID healthPolicyId,
        String healthPolicyNumber,
        String mothersName,
        String fathersName,
        @NotNull Zone zone,
        @NotNull Site locality,
        @NotBlank String address,
        String phone,
        @NotBlank String mobile,
        @NotBlank @Email String email) {
}