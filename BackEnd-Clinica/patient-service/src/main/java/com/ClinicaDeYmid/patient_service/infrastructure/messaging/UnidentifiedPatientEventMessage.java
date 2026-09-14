package com.ClinicaDeYmid.patient_service.infrastructure.messaging;

import com.ClinicaDeYmid.patient_service.domain.Sex;
import com.ClinicaDeYmid.patient_service.domain.UnidentifiedPatient;
import com.ClinicaDeYmid.patient_service.domain.UnidentifiedPatientEvent;
import com.ClinicaDeYmid.patient_service.domain.UnidentifiedPatientStatus;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
record UnidentifiedPatientEventMessage(
        UUID eventId,
        String type,
        int schemaVersion,
        Instant occurredAt,
        UUID patientUuid,
        long patientVersion,
        String traceId,
        Data data) {

    static UnidentifiedPatientEventMessage of(UnidentifiedPatientEvent event, UnidentifiedPatient patient, UUID eventId,
                                              Instant occurredAt, String traceId) {
        UUID previousPatientUuid = event instanceof UnidentifiedPatientEvent.IdentificationReverted reverted
                ? reverted.previousPatientUuid()
                : null;
        return new UnidentifiedPatientEventMessage(eventId, typeOf(event), PatientEventMessage.SCHEMA_VERSION, occurredAt,
                patient.uuid(), patient.version(), traceId,
                new Data(UnidentifiedPatientData.from(patient), previousPatientUuid));
    }

    static String typeOf(UnidentifiedPatientEvent event) {
        return switch (event) {
            case UnidentifiedPatientEvent.Registered registered -> "UnidentifiedPatientRegistered";
            case UnidentifiedPatientEvent.Identified identified -> "UnidentifiedPatientIdentified";
            case UnidentifiedPatientEvent.IdentificationReverted reverted -> "UnidentifiedPatientIdentificationReverted";
            case UnidentifiedPatientEvent.Died died -> "UnidentifiedPatientDied";
        };
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record Data(UnidentifiedPatientData unidentifiedPatient, UUID previousPatientUuid) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record UnidentifiedPatientData(
            UUID uuid,
            String code,
            Sex sex,
            int estimatedBirthYear,
            UnidentifiedPatientStatus.Code status,
            UUID identifiedPatientUuid,
            LocalDate dateOfDeath) {

        static UnidentifiedPatientData from(UnidentifiedPatient patient) {
            UnidentifiedPatientStatus status = patient.status();
            UUID identifiedPatientUuid = status instanceof UnidentifiedPatientStatus.Identified identified
                    ? identified.patientUuid()
                    : null;
            LocalDate dateOfDeath = status instanceof UnidentifiedPatientStatus.Deceased deceased ? deceased.dateOfDeath() : null;
            return new UnidentifiedPatientData(patient.uuid(), patient.code(), patient.sex(), patient.estimatedBirthYear(),
                    status.code(), identifiedPatientUuid, dateOfDeath);
        }
    }
}
