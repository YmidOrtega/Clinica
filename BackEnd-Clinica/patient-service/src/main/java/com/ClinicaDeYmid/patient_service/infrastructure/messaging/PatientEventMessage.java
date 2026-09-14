package com.ClinicaDeYmid.patient_service.infrastructure.messaging;

import com.ClinicaDeYmid.patient_service.domain.Affiliation;
import com.ClinicaDeYmid.patient_service.domain.Demographics;
import com.ClinicaDeYmid.patient_service.domain.DocumentType;
import com.ClinicaDeYmid.patient_service.domain.HealthRegime;
import com.ClinicaDeYmid.patient_service.domain.IdentityDocument;
import com.ClinicaDeYmid.patient_service.domain.Patient;
import com.ClinicaDeYmid.patient_service.domain.PatientEvent;
import com.ClinicaDeYmid.patient_service.domain.PatientStatus;
import com.ClinicaDeYmid.patient_service.domain.Sex;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
record PatientEventMessage(
        UUID eventId,
        String type,
        int schemaVersion,
        Instant occurredAt,
        UUID patientUuid,
        long patientVersion,
        String traceId,
        Data data) {

    static final int SCHEMA_VERSION = 1;

    static PatientEventMessage of(PatientEvent event, Patient patient, UUID eventId, Instant occurredAt, String traceId) {
        DocumentData previousDocument = event instanceof PatientEvent.DocumentChanged changed
                ? DocumentData.from(changed.previousDocument())
                : null;
        return new PatientEventMessage(eventId, typeOf(event), SCHEMA_VERSION, occurredAt, patient.uuid(), patient.version(),
                traceId, new Data(PatientData.from(patient), previousDocument));
    }

    static String typeOf(PatientEvent event) {
        return switch (event) {
            case PatientEvent.Registered registered -> "PatientRegistered";
            case PatientEvent.DocumentChanged documentChanged -> "PatientDocumentChanged";
            case PatientEvent.DemographicsCorrected demographicsCorrected -> "PatientDemographicsCorrected";
            case PatientEvent.AffiliationUpdated affiliationUpdated -> "PatientAffiliationUpdated";
            case PatientEvent.Deactivated deactivated -> "PatientDeactivated";
            case PatientEvent.Reactivated reactivated -> "PatientReactivated";
            case PatientEvent.Died died -> "PatientDied";
        };
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record Data(PatientData patient, DocumentData previousDocument) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record PatientData(
            UUID uuid,
            DocumentData document,
            String firstNames,
            String lastNames,
            LocalDate birthDate,
            Sex sex,
            PatientStatus.Code status,
            LocalDate dateOfDeath,
            AffiliationData affiliation) {

        static PatientData from(Patient patient) {
            Demographics demographics = patient.demographics();
            PatientStatus status = patient.status();
            LocalDate dateOfDeath = status instanceof PatientStatus.Deceased deceased ? deceased.dateOfDeath() : null;
            return new PatientData(patient.uuid(), DocumentData.from(patient.document()),
                    demographics.name().firstNames(), demographics.name().lastNames(), demographics.birthDate(),
                    demographics.sex(), status.code(), dateOfDeath, AffiliationData.from(patient.affiliation()));
        }
    }

    record DocumentData(DocumentType type, String number) {
        static DocumentData from(IdentityDocument document) {
            return new DocumentData(document.type(), document.number());
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record AffiliationData(HealthRegime regime, String healthProviderNit) {
        static AffiliationData from(Affiliation affiliation) {
            return new AffiliationData(affiliation.regime(), affiliation.healthProviderNit());
        }
    }
}
