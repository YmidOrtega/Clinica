package com.ClinicaDeYmid.patient_service.module.dto.patient;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;

public record UpdatePatientDto(
        @Schema(description = "Type of identification document", example = "CC")
        String identificationType,

        @Schema(description = "First name", example = "Yamid")
        String name,

        @Schema(description = "Last name", example = "Ortega")
        String lastName,

        @Schema(description = "Date of birth", example = "1990-05-10")
        LocalDate dateOfBirth,

        @Schema(description = "Place of birth", example = "Bogotá")
        String placeOfBirthName,

        @Schema(description = "Place of issuance of identification", example = "Bogotá")
        String placeOfIssuanceName,

        @Schema(description = "Disability", example = "Ninguna")
        String disability,

        @Schema(description = "Primary language", example = "Español")
        String language,

        @Schema(description = "Gender", example = "Masculino")
        String gender,

        @Schema(description = "Occupation", example = "Ingeniero de sistemas")
        String occupationName,

        @Schema(description = "Marital status", example = "Soltero")
        String maritalStatus,

        @Schema(description = "Religion", example = "Católica")
        String religion,

        @Schema(description = "Type of affiliation", example = "Contributivo")
        String typeOfAffiliation,

        @Schema(description = "Affiliation number", example = "AFF-0001")
        String affiliationNumber,

        @Schema(description = "NIT (Tax Identification Number) of the healthcare provider associated with the patient.", example = "123456789")
        String healthProviderNit,

        @Schema(description = "Health policy ID", example = "HPI-12345")
        String healthPolicyId,

        @Schema(description = "Health policy number", example = "POL-0001")
        String healthPolicyNumber,

        @Schema(description = "Mother's name", example = "Ana María Restrepo")
        String mothersName,

        @Schema(description = "Father's name", example = "Luis Ortega")
        String fathersName,

        @Schema(description = "Zone", example = "Urbana")
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
) {
}
