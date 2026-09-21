package com.ClinicaDeYmid.admissions_service.infrastructure.messaging;

import com.ClinicaDeYmid.admissions_service.domain.Admission;
import com.ClinicaDeYmid.admissions_service.domain.AdmissionEvent;
import com.ClinicaDeYmid.admissions_service.domain.AdmissionKind;
import com.ClinicaDeYmid.admissions_service.domain.AdmissionStatus;
import com.ClinicaDeYmid.admissions_service.domain.Coverage;
import com.ClinicaDeYmid.admissions_service.domain.Discharge;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
record AdmissionEventMessage(
        UUID eventId,
        String type,
        int schemaVersion,
        Instant occurredAt,
        UUID admissionUuid,
        long admissionVersion,
        String traceId,
        Data data) {

    static final int SCHEMA_VERSION = 1;

    static AdmissionEventMessage of(AdmissionEvent event, Admission admission, UUID eventId, Instant occurredAt,
                                    String traceId) {
        return new AdmissionEventMessage(eventId, typeOf(event), SCHEMA_VERSION, occurredAt, admission.uuid(),
                admission.version(), traceId, Data.of(event, admission));
    }

    static String typeOf(AdmissionEvent event) {
        return switch (event) {
            case AdmissionEvent.Registered registered -> "AdmissionRegistered";
            case AdmissionEvent.PhaseChanged phaseChanged -> "AdmissionPhaseChanged";
            case AdmissionEvent.BedAssigned bedAssigned -> "AdmissionBedAssigned";
            case AdmissionEvent.BedReleased bedReleased -> "AdmissionBedReleased";
            case AdmissionEvent.CoveragePending coveragePending -> "AdmissionCoveragePending";
            case AdmissionEvent.Discharged discharged -> "AdmissionDischarged";
            case AdmissionEvent.Cancelled cancelled -> "AdmissionCancelled";
        };
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record Data(AdmissionData admission, UUID previousServiceUuid, String reason, UUID bedUuid,
                UUID previousBedUuid, String coverageDetail, Discharge.Code discharge) {

        static Data of(AdmissionEvent event, Admission admission) {
            AdmissionData snapshot = AdmissionData.from(admission);
            return switch (event) {
                case AdmissionEvent.Registered registered ->
                        new Data(snapshot, null, null, null, null, null, null);
                case AdmissionEvent.PhaseChanged phaseChanged ->
                        new Data(snapshot, phaseChanged.previousServiceUuid(), phaseChanged.reason(), null, null,
                                null, null);
                case AdmissionEvent.BedAssigned bedAssigned ->
                        new Data(snapshot, null, null, bedAssigned.bedUuid(), bedAssigned.previousBedUuid(), null,
                                null);
                case AdmissionEvent.BedReleased bedReleased ->
                        new Data(snapshot, null, null, bedReleased.bedUuid(), null, null, null);
                case AdmissionEvent.CoveragePending coveragePending ->
                        new Data(snapshot, null, null, null, null, coveragePending.detail(), null);
                case AdmissionEvent.Discharged discharged ->
                        new Data(snapshot, null, null, null, null, null, discharged.discharge());
                case AdmissionEvent.Cancelled cancelled ->
                        new Data(snapshot, null, cancelled.reason(), null, null, null, null);
            };
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record AdmissionData(
            UUID uuid,
            String number,
            UUID patientUuid,
            AdmissionKind kind,
            AdmissionStatus.Code status,
            UUID configurationServiceUuid,
            UUID bedUuid,
            Coverage.Code coverage) {

        static AdmissionData from(Admission admission) {
            return new AdmissionData(admission.uuid(), admission.number(), admission.patientUuid(), admission.kind(),
                    admission.status().code(), admission.lastPhase().configurationService().uuid(),
                    admission.bedUuid(), admission.coverage() == null ? null : admission.coverage().status());
        }
    }
}
