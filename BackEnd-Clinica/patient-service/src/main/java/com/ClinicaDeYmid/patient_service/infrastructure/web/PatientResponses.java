package com.ClinicaDeYmid.patient_service.infrastructure.web;

import com.ClinicaDeYmid.patient_service.application.HealthProviderLookup;
import com.ClinicaDeYmid.patient_service.application.PatientHistory;
import com.ClinicaDeYmid.patient_service.domain.AffiliateType;
import com.ClinicaDeYmid.patient_service.domain.Affiliation;
import com.ClinicaDeYmid.patient_service.domain.ContactInfo;
import com.ClinicaDeYmid.patient_service.domain.Demographics;
import com.ClinicaDeYmid.patient_service.domain.Disability;
import com.ClinicaDeYmid.patient_service.domain.DocumentType;
import com.ClinicaDeYmid.patient_service.domain.EmergencyContact;
import com.ClinicaDeYmid.patient_service.domain.HealthRegime;
import com.ClinicaDeYmid.patient_service.domain.IdentityDocument;
import com.ClinicaDeYmid.patient_service.domain.Patient;
import com.ClinicaDeYmid.patient_service.domain.PatientStatus;
import com.ClinicaDeYmid.patient_service.domain.Relationship;
import com.ClinicaDeYmid.patient_service.domain.Residence;
import com.ClinicaDeYmid.patient_service.domain.Sex;
import com.ClinicaDeYmid.patient_service.domain.Zone;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonUnwrapped;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

final class PatientResponses {

    private PatientResponses() {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record PatientView(
            UUID uuid,
            long version,
            DocumentView document,
            DemographicsView demographics,
            ContactView contact,
            EmergencyContactView emergencyContact,
            AffiliationView affiliation,
            ResidenceView residence,
            StatusView status,
            AuditView audit) {

        static PatientView from(Patient patient) {
            return new PatientView(
                    patient.uuid(),
                    patient.version(),
                    DocumentView.from(patient.document()),
                    DemographicsView.from(patient.demographics()),
                    ContactView.from(patient.contact()),
                    EmergencyContactView.from(patient.emergencyContact()),
                    AffiliationView.from(patient.affiliation()),
                    ResidenceView.from(patient.residence()),
                    StatusView.from(patient.status(), patient),
                    new AuditView(patient.createdAt(), patient.createdBy(), patient.updatedAt(), patient.updatedBy()));
        }
    }

    record PatientDetailsView(@JsonUnwrapped PatientView patient, HealthProviderView healthProvider) {
    }

    record PatientSummaryView(UUID uuid, DocumentView document, String firstNames, String lastNames,
                              LocalDate birthDate, Sex sex, PatientStatus.Code status) {

        static PatientSummaryView from(Patient patient) {
            Demographics demographics = patient.demographics();
            return new PatientSummaryView(patient.uuid(), DocumentView.from(patient.document()),
                    demographics.name().firstNames(), demographics.name().lastNames(),
                    demographics.birthDate(), demographics.sex(), patient.status().code());
        }
    }

    record RevisionView(long number, Instant revisedAt, String revisedBy, PatientHistory.ChangeType changeType, PatientView state) {

        static RevisionView from(PatientHistory.Revision revision) {
            return new RevisionView(revision.number(), revision.revisedAt(), revision.revisedBy(), revision.changeType(),
                    PatientView.from(revision.state()));
        }
    }

    record DocumentView(DocumentType type, String number) {
        static DocumentView from(IdentityDocument document) {
            return new DocumentView(document.type(), document.number());
        }
    }

    record DemographicsView(String firstNames, String lastNames, LocalDate birthDate, Sex sex,
                            String countryOfOrigin, Disability disability) {
        static DemographicsView from(Demographics demographics) {
            return new DemographicsView(demographics.name().firstNames(), demographics.name().lastNames(),
                    demographics.birthDate(), demographics.sex(), demographics.countryOfOrigin(), demographics.disability());
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record ContactView(String mobile, String phone, String email) {
        static ContactView from(ContactInfo contact) {
            return new ContactView(contact.mobile(), contact.phone(), contact.email());
        }
    }

    record EmergencyContactView(String fullName, Relationship relationship, String phone) {
        static EmergencyContactView from(EmergencyContact contact) {
            return contact == null ? null : new EmergencyContactView(contact.fullName(), contact.relationship(), contact.phone());
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record AffiliationView(HealthRegime regime, AffiliateType affiliateType, String healthProviderNit, String policyNumber) {
        static AffiliationView from(Affiliation affiliation) {
            return new AffiliationView(affiliation.regime(), affiliation.affiliateType(),
                    affiliation.healthProviderNit(), affiliation.policyNumber());
        }
    }

    record ResidenceView(String department, String municipality, Zone zone, String address) {
        static ResidenceView from(Residence residence) {
            return new ResidenceView(residence.department(), residence.municipality(), residence.zone(), residence.address());
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record StatusView(PatientStatus.Code code, String reason, Instant changedAt, LocalDate dateOfDeath) {
        static StatusView from(PatientStatus status, Patient patient) {
            return switch (status) {
                case PatientStatus.Active active -> new StatusView(status.code(), null, patient.statusChangedAt(), null);
                case PatientStatus.Inactive inactive -> new StatusView(status.code(), inactive.reason(), inactive.since(), null);
                case PatientStatus.Deceased deceased ->
                        new StatusView(status.code(), null, patient.statusChangedAt(), deceased.dateOfDeath());
            };
        }
    }

    record AuditView(Instant createdAt, String createdBy, Instant updatedAt, String updatedBy) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record HealthProviderView(Availability availability, String name, String type) {

        enum Availability {
            AVAILABLE,
            NOT_FOUND,
            UNAVAILABLE,
            NOT_AFFILIATED
        }

        static HealthProviderView from(HealthProviderLookup lookup) {
            return switch (lookup) {
                case HealthProviderLookup.Found found ->
                        new HealthProviderView(Availability.AVAILABLE, found.provider().name(), found.provider().type());
                case HealthProviderLookup.NotFound notFound -> new HealthProviderView(Availability.NOT_FOUND, null, null);
                case HealthProviderLookup.Unavailable unavailable -> new HealthProviderView(Availability.UNAVAILABLE, null, null);
                case HealthProviderLookup.NotAffiliated notAffiliated ->
                        new HealthProviderView(Availability.NOT_AFFILIATED, null, null);
            };
        }
    }
}
