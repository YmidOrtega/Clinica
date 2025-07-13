package com.ClinicaDeYmid.patient_service.module.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;

public record GetPatientDto(
        @Schema(description = "Unique identifier for the patient (UUID)", example = "123e4567-e89b-12d3-a456-426614174000")
        String uuid,

        @Schema(description = "Type of identification document", example = "CEDULA_DE_CIUDADANIA")
        String identificationType,

        @Schema(description = "Identification number", example = "1234567890")
        String identificationNumber,

        @Schema(description = "First name of the patient", example = "Yamid")
        String name,

        @Schema(description = "Last name of the patient", example = "Ortega")
        String lastName,

        @Schema(description = "Date of birth", example = "1990-05-10")
        LocalDate dateOfBirth,

        @Schema(description = "Place of birth", example = "Bogotá")
        String placeOfBirthName,

        @Schema(description = "Place of issuance of identification", example = "Bogotá")
        String placeOfIssuanceName,

        @Schema(description = "Disability description", example = "")
        String disability,

        @Schema(description = "Primary language", example = "SPANISH")
        String language,

        @Schema(description = "Gender", example = "MASCULINE")
        String gender,

        @Schema(description = "Occupation name", example = "Ingeniero de sistemas")
        String occupationName,

        @Schema(description = "Marital status", example = "Soltero")
        String maritalStatus,

        @Schema(description = "Religion", example = "Católica")
        String religion,

        @Schema(description = "Type of affiliation", example = "CONTRIBUTOR")
        String typeOfAffiliation,

        @Schema(description = "Affiliation number", example = "AFF-0001")
        String affiliationNumber,

        @Schema(description = "Client information")
        GetClientDto clientInfo,

        @Schema(description = "Health policy number", example = "POL-0001")
        String healthPolicyNumber,

        @Schema(description = "Mother's name", example = "Ana María Restrepo")
        String mothersName,

        @Schema(description = "Father's name", example = "Luis Ortega")
        String fathersName,

        @Schema(description = "Zone of residence", example = "Urbana")
        String zone,

        @Schema(description = "Locality name", example = "Chapinero")
        String localityName,

        @Schema(description = "Home address", example = "Calle 123 #45-67")
        String address,

        @Schema(description = "Phone number", example = "6011234567")
        String phone,

        @Schema(description = "Mobile number", example = "3011234567")
        String mobile,

        @Schema(description = "Email address", example = "yamid@example.com")
        String email
) {}
