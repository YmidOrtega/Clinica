package com.ClinicaDeYmid.patient_service.infrastructure.web;

import com.ClinicaDeYmid.patient_service.domain.AffiliateType;
import com.ClinicaDeYmid.patient_service.domain.Affiliation;
import com.ClinicaDeYmid.patient_service.domain.ContactInfo;
import com.ClinicaDeYmid.patient_service.domain.Demographics;
import com.ClinicaDeYmid.patient_service.domain.Disability;
import com.ClinicaDeYmid.patient_service.domain.DocumentType;
import com.ClinicaDeYmid.patient_service.domain.EmergencyContact;
import com.ClinicaDeYmid.patient_service.domain.HealthRegime;
import com.ClinicaDeYmid.patient_service.domain.IdentityDocument;
import com.ClinicaDeYmid.patient_service.domain.PatientRegistration;
import com.ClinicaDeYmid.patient_service.domain.PersonName;
import com.ClinicaDeYmid.patient_service.domain.Relationship;
import com.ClinicaDeYmid.patient_service.domain.Residence;
import com.ClinicaDeYmid.patient_service.domain.Sex;
import com.ClinicaDeYmid.patient_service.domain.Zone;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

final class PatientRequests {

    private static final String REQUIRED = "es obligatorio";

    private PatientRequests() {
    }

    record Register(
            @NotNull(message = REQUIRED) @Valid Document document,
            @NotNull(message = REQUIRED) @Valid DemographicData demographics,
            @NotNull(message = REQUIRED) @Valid Contact contact,
            @Valid Emergency emergencyContact,
            @NotNull(message = REQUIRED) @Valid AffiliationData affiliation,
            @NotNull(message = REQUIRED) @Valid ResidenceData residence) {

        PatientRegistration toDomain() {
            return new PatientRegistration(document.toDomain(), demographics.toDomain(), contact.toDomain(),
                    Emergency.toDomain(emergencyContact), affiliation.toDomain(), residence.toDomain());
        }
    }

    record Document(DocumentType type, String number) {
        IdentityDocument toDomain() {
            return new IdentityDocument(type, number);
        }
    }

    record DemographicData(String firstNames, String lastNames, LocalDate birthDate, Sex sex,
                           String countryOfOrigin, Disability disability) {
        Demographics toDomain() {
            return new Demographics(new PersonName(firstNames, lastNames), birthDate, sex, countryOfOrigin, disability);
        }
    }

    record Contact(String mobile, String phone, String email) {
        ContactInfo toDomain() {
            return new ContactInfo(mobile, phone, email);
        }
    }

    record Emergency(String fullName, Relationship relationship, String phone) {
        static EmergencyContact toDomain(Emergency emergency) {
            return emergency == null ? null : new EmergencyContact(emergency.fullName, emergency.relationship, emergency.phone);
        }
    }

    record ContactUpdate(@NotNull(message = REQUIRED) @Valid Contact contact, @Valid Emergency emergencyContact) {
    }

    record AffiliationData(HealthRegime regime, AffiliateType affiliateType, String healthProviderNit, String policyNumber) {
        Affiliation toDomain() {
            return new Affiliation(regime, affiliateType, healthProviderNit, policyNumber);
        }
    }

    record ResidenceData(String department, String municipality, Zone zone, String address) {
        Residence toDomain() {
            return new Residence(department, municipality, zone, address);
        }
    }

    record UnidentifiedRegistration(Sex sex, Integer estimatedBirthYear, String description) {
    }

    record Identification(UUID patientUuid, @Valid Register registration, String reason) {
    }

    record Reversal(String reason) {
    }

    record Search(@Valid Document document, @Valid NameSearch name) {
    }

    record NameSearch(String lastNames, String firstNames) {
    }

    record Deactivation(String reason) {
    }

    record Death(LocalDate dateOfDeath) {
    }
}
